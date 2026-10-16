package com.codetutor.backend.analysis;

import com.codetutor.backend.analysis.model.AnalysisModel.*;
import com.codetutor.backend.analysis.util.TypeUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Builds the database schema (tables, columns, constraints, relationships and cardinality) from JPA
 * entities, and enriches / complements it with any SQL {@code CREATE TABLE} scripts found in the repository.
 */
public class SchemaAnalyzer {

    private final Map<String, ClassInfo> byId = new HashMap<>();
    private final Map<String, Table> tables = new LinkedHashMap<>();
    private final Map<String, List<Column>> columns = new LinkedHashMap<>();
    private final Map<String, String> tableEntity = new LinkedHashMap<>();
    private final Map<String, String> tableFile = new LinkedHashMap<>();
    private final Map<String, Integer> tableLine = new LinkedHashMap<>();
    private final Map<String, String> tableSource = new LinkedHashMap<>();
    private final List<Relation> relations = new ArrayList<>();
    private final Map<String, String> entityTable = new LinkedHashMap<>(); // class id -> table name

    public Schema analyze(Path root, List<ClassInfo> classes, List<String> sqlFiles) {
        for (ClassInfo c : classes) byId.put(c.id(), c);

        // 1. table names for every entity
        List<ClassInfo> entities = classes.stream().filter(c -> !c.test() && isTableEntity(c)).toList();
        for (ClassInfo e : entities) entityTable.put(e.id(), tableName(e));

        // 2. columns + relations
        for (ClassInfo e : entities) buildEntity(e);

        // 3. SQL scripts
        parseSqlFiles(root, sqlFiles);

        // 4. assemble
        List<Table> out = new ArrayList<>();
        for (var en : columns.entrySet()) {
            String name = en.getKey();
            List<Column> cols = en.getValue();
            String entity = tableEntity.get(name);
            String desc = describe(name, entity, cols);
            out.add(new Table(name, entity, tableFile.get(name), tableLine.getOrDefault(name, 1), cols,
                    tableSource.getOrDefault(name, "JPA"), desc));
        }
        // add cardinality label text
        return new Schema(out, dedupe(relations));
    }

    /** Maps an entity class id to its table name (used by the flow generator). */
    public Map<String, String> entityTables() { return entityTable; }

    // ───────────────────────── JPA ─────────────────────────

    private boolean isTableEntity(ClassInfo c) {
        boolean entity = has(c.annotations(), "Entity");
        boolean table = has(c.annotations(), "Table");
        boolean doc = has(c.annotations(), "Document");
        return entity || doc || (table && !has(c.annotations(), "MappedSuperclass"));
    }

    private String tableName(ClassInfo c) {
        Ann t = ann(c.annotations(), "Table");
        if (t != null && t.arg("name") != null) return unq(t.arg("name"));
        Ann d = ann(c.annotations(), "Document");
        if (d != null) {
            String v = d.arg("collection") != null ? d.arg("collection") : d.arg("value");
            if (v != null) return unq(v);
        }
        Ann en = ann(c.annotations(), "Entity");
        if (en != null && en.arg("name") != null) return TypeUtil.snake(unq(en.arg("name")));
        return TypeUtil.snake(c.name());
    }

    private void buildEntity(ClassInfo e) {
        String table = entityTable.get(e.id());
        // a parent entity may already have registered a foreign-key column on this table (unidirectional @OneToMany)
        List<Column> cols = columns.computeIfAbsent(table, k -> new ArrayList<>());
        tableEntity.put(table, e.id());
        tableFile.put(table, e.file());
        tableLine.put(table, e.line());
        tableSource.put(table, "JPA");

        for (ClassInfo owner : hierarchy(e)) {
            for (FieldInfo f : owner.fields()) {
                if (f.isStatic() || has(f.annotations(), "Transient")) continue;
                handleField(e, owner, table, cols, f);
            }
        }
    }

    /** The entity itself plus every superclass that contributes columns (@MappedSuperclass or entity parents). */
    private List<ClassInfo> hierarchy(ClassInfo e) {
        List<ClassInfo> chain = new ArrayList<>();
        Deque<ClassInfo> stack = new ArrayDeque<>();
        ClassInfo cur = e;
        Set<String> seen = new HashSet<>();
        while (cur != null && seen.add(cur.id())) {
            stack.push(cur);
            ClassInfo sup = null;
            if (cur.extendsType() != null) {
                String simple = TypeUtil.raw(cur.extendsType());
                for (Dep d : cur.dependsOn()) {
                    if ("EXTENDS".equals(d.kind()) && byId.containsKey(d.target())
                            && byId.get(d.target()).name().equals(simple)) sup = byId.get(d.target());
                }
            }
            cur = sup;
        }
        while (!stack.isEmpty()) chain.add(stack.pop());
        return chain;
    }

    private void handleField(ClassInfo entity, ClassInfo owner, String table, List<Column> cols, FieldInfo f) {
        List<Ann> an = f.annotations();
        String targetEntity = targetEntityOf(entity, f);

        if (has(an, "OneToMany") || has(an, "ManyToMany") || has(an, "OneToOne") || has(an, "ManyToOne")) {
            relationField(entity, table, cols, f, targetEntity);
            return;
        }
        if (has(an, "Embedded") || has(an, "EmbeddedId")) {
            ClassInfo emb = lookup(entity, f.type());
            if (emb != null) {
                for (FieldInfo ef : emb.fields()) {
                    if (!ef.isStatic() && !has(ef.annotations(), "Transient")) basicColumn(cols, ef, has(an, "EmbeddedId"));
                }
            }
            return;
        }
        if (has(an, "ElementCollection")) return;
        if (isCollection(f.type()) && lookup(entity, TypeUtil.typeArgs(f.type()).isEmpty() ? null
                : TypeUtil.typeArgs(f.type()).get(0)) != null) return; // unmapped association
        basicColumn(cols, f, has(an, "Id"));
    }

    private void basicColumn(List<Column> cols, FieldInfo f, boolean pk) {
        List<Ann> an = f.annotations();
        Ann col = ann(an, "Column");
        String name = col != null && col.arg("name") != null ? unq(col.arg("name")) : TypeUtil.snake(f.name());
        boolean nullable = col == null || !"false".equals(col.arg("nullable"));
        boolean unique = col != null && "true".equals(col.arg("unique"));
        Integer length = col != null && col.arg("length") != null ? parseInt(col.arg("length")) : null;
        if (has(an, "NotNull") || has(an, "NotBlank") || has(an, "NotEmpty") || pk) nullable = false;
        Ann size = ann(an, "Size");
        if (size != null && size.arg("max") != null) length = parseInt(size.arg("max"));
        boolean generated = has(an, "GeneratedValue");
        boolean enumerated = has(an, "Enumerated");
        String sql = col != null && col.arg("columnDefinition") != null ? unq(col.arg("columnDefinition"))
                : sqlType(f.type(), length, enumerated);
        cols.removeIf(c -> c.name().equals(name));
        cols.add(new Column(name, sql, f.type(), nullable, unique, pk, generated, length, null, f.name()));
    }

    private void relationField(ClassInfo entity, String table, List<Column> cols, FieldInfo f, String targetEntity) {
        List<Ann> an = f.annotations();
        ClassInfo target = lookup(entity, targetEntity);
        if (target == null) return;
        String targetTable = entityTable.getOrDefault(target.id(), TypeUtil.snake(target.name()));
        Ann join = ann(an, "JoinColumn");
        String fkName = join != null && join.arg("name") != null ? unq(join.arg("name")) : null;

        if (has(an, "ManyToOne") || has(an, "OneToOne")) {
            Ann rel = ann(an, has(an, "ManyToOne") ? "ManyToOne" : "OneToOne");
            boolean inverse = rel != null && rel.arg("mappedBy") != null;
            if (inverse) return; // owned by the other side
            String fk = fkName != null ? fkName : TypeUtil.snake(f.name()) + "_id";
            boolean optional = rel == null || !"false".equals(rel.arg("optional"));
            boolean oneToOne = has(an, "OneToOne");
            String pk = pkOf(target);
            cols.removeIf(c -> c.name().equals(fk));
            cols.add(new Column(fk, sqlFor(target, pk), pkJavaType(target), optional, oneToOne, false, false, null,
                    targetTable + "." + pk, f.name()));
            relations.add(new Relation(table, targetTable, fk, pk, oneToOne ? "ONE_TO_ONE" : "MANY_TO_ONE",
                    oneToOne ? "1:1" : "N:1", f.name(), null, null));
        } else if (has(an, "OneToMany")) {
            Ann rel = ann(an, "OneToMany");
            String mappedBy = rel != null && rel.arg("mappedBy") != null ? unq(rel.arg("mappedBy")) : null;
            if (mappedBy != null) {
                // FK lives on the child; make sure the child's column/relation is registered when we build it
                // (handled when the child entity declares the ManyToOne). Nothing to add here.
                return;
            }
            // unidirectional one-to-many: FK column on the child table
            String fk = fkName != null ? fkName : TypeUtil.snake(entity.name()) + "_id";
            String childTable = targetTable;
            String pk = pkOf(entity);
            List<Column> childCols = columns.computeIfAbsent(childTable, k -> new ArrayList<>());
            if (childCols.stream().noneMatch(c -> c.name().equals(fk))) {
                childCols.add(new Column(fk, sqlFor(entity, pk), pkJavaType(entity), true, false, false, false, null,
                        table + "." + pk, entity.name() + "." + f.name()));
            }
            relations.add(new Relation(childTable, table, fk, pk, "MANY_TO_ONE", "N:1", f.name(), null, null));
        } else if (has(an, "ManyToMany")) {
            Ann rel = ann(an, "ManyToMany");
            if (rel != null && rel.arg("mappedBy") != null) return; // inverse side
            Ann jt = ann(an, "JoinTable");
            String joinTable = jt != null && jt.arg("name") != null ? unq(jt.arg("name"))
                    : table + "_" + targetTable;
            String jc = jt != null ? nestedName(jt.arg("joinColumns")) : null;
            String ic = jt != null ? nestedName(jt.arg("inverseJoinColumns")) : null;
            if (jc == null) jc = TypeUtil.snake(entity.name()) + "_id";
            if (ic == null) ic = TypeUtil.snake(target.name()) + "_id";
            String pk1 = pkOf(entity);
            String pk2 = pkOf(target);
            List<Column> jcols = columns.computeIfAbsent(joinTable, k -> new ArrayList<>());
            tableSource.putIfAbsent(joinTable, "JPA");
            tableFile.putIfAbsent(joinTable, entity.file());
            tableLine.putIfAbsent(joinTable, f.line());
            jcols.add(new Column(jc, sqlFor(entity, pk1), pkJavaType(entity), false, false, true, false, null,
                    table + "." + pk1, entity.name() + "." + f.name()));
            jcols.add(new Column(ic, sqlFor(target, pk2), pkJavaType(target), false, false, true, false, null,
                    targetTable + "." + pk2, f.name()));
            relations.add(new Relation(joinTable, table, jc, pk1, "MANY_TO_ONE", "N:1", f.name(), joinTable, null));
            relations.add(new Relation(joinTable, targetTable, ic, pk2, "MANY_TO_ONE", "N:1", f.name(), joinTable,
                    null));
        }
    }

    private ClassInfo lookup(ClassInfo ctx, String type) {
        if (type == null) return null;
        String raw = TypeUtil.raw(type);
        for (Dep d : ctx.dependsOn()) {
            ClassInfo t = byId.get(d.target());
            if (t != null && t.name().equals(raw)) return t;
        }
        for (ClassInfo c : byId.values()) if (c.name().equals(raw) && c.pkg().equals(ctx.pkg())) return c;
        return null;
    }

    private String targetEntityOf(ClassInfo ctx, FieldInfo f) {
        Ann rel = null;
        for (String n : List.of("OneToMany", "ManyToMany", "OneToOne", "ManyToOne")) {
            if (ann(f.annotations(), n) != null) rel = ann(f.annotations(), n);
        }
        if (rel != null && rel.arg("targetEntity") != null) {
            return unq(rel.arg("targetEntity")).replace(".class", "");
        }
        if (isCollection(f.type())) {
            List<String> a = TypeUtil.typeArgs(f.type());
            return a.isEmpty() ? null : a.get(0);
        }
        return f.type();
    }

    private String pkOf(ClassInfo e) {
        for (ClassInfo c : hierarchy(e)) {
            for (FieldInfo f : c.fields()) {
                if (has(f.annotations(), "Id") || has(f.annotations(), "EmbeddedId")) {
                    Ann col = ann(f.annotations(), "Column");
                    return col != null && col.arg("name") != null ? unq(col.arg("name")) : TypeUtil.snake(f.name());
                }
            }
        }
        return "id";
    }

    private String pkJavaType(ClassInfo e) {
        for (ClassInfo c : hierarchy(e)) {
            for (FieldInfo f : c.fields()) if (has(f.annotations(), "Id")) return f.type();
        }
        return "Long";
    }

    private String sqlFor(ClassInfo e, String pk) { return sqlType(pkJavaType(e), null, false); }

    private static String nestedName(String rendered) {
        if (rendered == null) return null;
        Matcher m = Pattern.compile("name\\s*=\\s*\"([^\"]+)\"").matcher(rendered);
        return m.find() ? m.group(1) : null;
    }

    static String sqlType(String javaType, Integer length, boolean enumerated) {
        String t = TypeUtil.raw(javaType);
        if (t == null) return "VARCHAR";
        if (enumerated) return "VARCHAR(" + (length != null ? length : 255) + ")";
        return switch (t) {
            case "String", "char", "Character" -> "VARCHAR(" + (length != null ? length : 255) + ")";
            case "int", "Integer", "short", "Short" -> "INTEGER";
            case "long", "Long" -> "BIGINT";
            case "boolean", "Boolean" -> "BOOLEAN";
            case "double", "Double", "float", "Float" -> "DOUBLE";
            case "BigDecimal" -> "DECIMAL(19,2)";
            case "BigInteger" -> "NUMERIC";
            case "LocalDate", "Date" -> "DATE";
            case "LocalDateTime", "Instant", "ZonedDateTime", "OffsetDateTime", "Timestamp" -> "TIMESTAMP";
            case "LocalTime" -> "TIME";
            case "UUID" -> "UUID";
            case "byte[]", "Byte" -> "BLOB";
            default -> t.length() > 0 && Character.isUpperCase(t.charAt(0)) ? "VARCHAR(255)" : "VARCHAR";
        };
    }

    private static boolean isCollection(String t) {
        String r = TypeUtil.raw(t);
        return Set.of("List", "Set", "Collection", "Iterable", "SortedSet", "ArrayList", "HashSet").contains(r);
    }

    private static boolean has(List<Ann> an, String name) {
        return an != null && an.stream().anyMatch(a -> a.name().equals(name));
    }

    private static Ann ann(List<Ann> an, String name) {
        if (an == null) return null;
        return an.stream().filter(a -> a.name().equals(name)).findFirst().orElse(null);
    }

    static String unq(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length() - 1);
        return t;
    }

    private static Integer parseInt(String s) {
        try { return Integer.parseInt(unq(s).trim()); } catch (Exception e) { return null; }
    }

    // ───────────────────────── SQL scripts ─────────────────────────

    private static final Pattern CREATE = Pattern.compile(
            "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[`\"']?([\\w.]+)[`\"']?\\s*\\((.*?)\\)\\s*(?:ENGINE|DEFAULT|WITH|;|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private void parseSqlFiles(Path root, List<String> sqlFiles) {
        // prefer H2 / generic scripts first so a table is only defined once
        List<String> ordered = new ArrayList<>(sqlFiles);
        ordered.sort(Comparator.comparingInt(SchemaAnalyzer::dialectRank).thenComparing(s -> s));
        Set<String> sqlSeen = new HashSet<>();
        for (String rel : ordered) {
            String text;
            try { text = Files.readString(root.resolve(rel)); } catch (IOException e) { continue; }
            text = text.replaceAll("(?m)--.*$", "").replaceAll("(?s)/\\*.*?\\*/", "");
            String[] statements = text.split(";");
            for (String stmtRaw : statements) {
                String stmt = stmtRaw.trim() + ";";
                Matcher m = CREATE.matcher(stmt);
                if (!m.find()) continue;
                String name = m.group(1).toLowerCase(Locale.ROOT);
                if (name.contains(".")) name = name.substring(name.lastIndexOf('.') + 1);
                if (!sqlSeen.add(name)) continue; // already defined by a preferred dialect
                mergeSqlTable(rel, name, m.group(2));
            }
        }
    }

    private static int dialectRank(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.contains("h2")) return 0;
        if (p.contains("hsql")) return 1;
        if (p.contains("postgres")) return 2;
        if (p.contains("mysql")) return 3;
        if (p.contains("test")) return 9;
        return 4;
    }

    private void mergeSqlTable(String file, String table, String body) {
        List<Column> existing = columns.get(table);
        boolean fromJpa = existing != null;
        List<Column> cols = fromJpa ? existing : new ArrayList<>();
        Set<String> pkCols = new HashSet<>();
        Map<String, String> fks = new LinkedHashMap<>();
        Map<String, Column> parsed = new LinkedHashMap<>();

        for (String part : splitTop(body)) {
            String line = part.trim();
            if (line.isEmpty()) continue;
            String up = line.toUpperCase(Locale.ROOT);
            if (up.startsWith("PRIMARY KEY")) {
                for (String c : cols(line)) pkCols.add(c.toLowerCase(Locale.ROOT));
                continue;
            }
            Matcher fk = Pattern.compile("(?:CONSTRAINT\\s+\\S+\\s+)?FOREIGN\\s+KEY\\s*\\(([^)]+)\\)\\s*REFERENCES\\s+[`\"']?(\\w+)[`\"']?\\s*(?:\\(([^)]+)\\))?",
                    Pattern.CASE_INSENSITIVE).matcher(line);
            if (fk.find()) {
                String c = fk.group(1).trim().replace("`", "").replace("\"", "").toLowerCase(Locale.ROOT);
                fks.put(c, fk.group(2).toLowerCase(Locale.ROOT) + "." + (fk.group(3) == null ? "id"
                        : fk.group(3).trim().toLowerCase(Locale.ROOT)));
                continue;
            }
            if (up.startsWith("CONSTRAINT") || up.startsWith("UNIQUE") || up.startsWith("KEY ")
                    || up.startsWith("INDEX") || up.startsWith("CHECK")) continue;

            Matcher cm = Pattern.compile("^[`\"']?(\\w+)[`\"']?\\s+([A-Za-z]+(?:\\s*\\([^)]*\\))?)(.*)$", Pattern.DOTALL)
                    .matcher(line);
            if (!cm.find()) continue;
            String name = cm.group(1).toLowerCase(Locale.ROOT);
            String type = cm.group(2).replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
            String rest = cm.group(3).toUpperCase(Locale.ROOT);
            boolean pk = rest.contains("PRIMARY KEY");
            boolean notNull = rest.contains("NOT NULL") || pk;
            boolean unique = rest.contains("UNIQUE");
            boolean gen = rest.contains("AUTO_INCREMENT") || rest.contains("IDENTITY") || rest.contains("SERIAL")
                    || rest.contains("GENERATED") || type.contains("SERIAL");
            Matcher ref = Pattern.compile("REFERENCES\\s+[`\"']?(\\w+)[`\"']?\\s*(?:\\(([^)]+)\\))?",
                    Pattern.CASE_INSENSITIVE).matcher(cm.group(3));
            String fkRef = null;
            if (ref.find()) {
                fkRef = ref.group(1).toLowerCase(Locale.ROOT) + "." + (ref.group(2) == null ? "id"
                        : ref.group(2).trim().toLowerCase(Locale.ROOT));
            }
            Integer len = null;
            Matcher lm = Pattern.compile("\\((\\d+)").matcher(type);
            if (lm.find()) len = Integer.parseInt(lm.group(1));
            parsed.put(name, new Column(name, type, null, !notNull, unique, pk, gen, len, fkRef, null));
        }
        for (String pkc : pkCols) {
            Column c = parsed.get(pkc);
            if (c != null) parsed.put(pkc, new Column(c.name(), c.type(), c.javaType(), false, c.unique(), true,
                    c.generated(), c.length(), c.foreignKey(), c.field()));
        }
        for (var fe : fks.entrySet()) {
            Column c = parsed.get(fe.getKey());
            if (c != null) parsed.put(fe.getKey(), new Column(c.name(), c.type(), c.javaType(), c.nullable(),
                    c.unique(), c.primaryKey(), c.generated(), c.length(), fe.getValue(), c.field()));
        }

        if (fromJpa) {
            // SQL has exact types/constraints: enrich the JPA columns rather than replacing them
            List<Column> merged = new ArrayList<>();
            for (Column jc : cols) {
                Column sc = parsed.get(jc.name());
                if (sc == null) { merged.add(jc); continue; }
                merged.add(new Column(jc.name(), sc.type(), jc.javaType(), sc.nullable() && jc.nullable(),
                        sc.unique() || jc.unique(), sc.primaryKey() || jc.primaryKey(),
                        sc.generated() || jc.generated(), sc.length() != null ? sc.length() : jc.length(),
                        jc.foreignKey() != null ? jc.foreignKey() : sc.foreignKey(), jc.field()));
            }
            for (var pe : parsed.entrySet()) {
                if (merged.stream().noneMatch(c -> c.name().equals(pe.getKey()))) merged.add(pe.getValue());
            }
            columns.put(table, merged);
            tableSource.put(table, "JPA+SQL");
            cols = merged;
        } else {
            cols.addAll(parsed.values());
            columns.put(table, cols);
            tableSource.put(table, "SQL");
            tableFile.put(table, file);
            tableLine.put(table, 1);
        }
        // relations from SQL foreign keys
        for (Column c : columns.get(table)) {
            if (c.foreignKey() == null) continue;
            String[] ft = c.foreignKey().split("\\.");
            boolean exists = relations.stream().anyMatch(r -> r.fromTable().equals(table)
                    && r.fromColumn().equals(c.name()) && r.toTable().equals(ft[0]));
            if (!exists) {
                relations.add(new Relation(table, ft[0], c.name(), ft.length > 1 ? ft[1] : "id", "MANY_TO_ONE", "N:1",
                        null, null, null));
            }
        }
    }

    private static List<String> cols(String line) {
        Matcher m = Pattern.compile("\\(([^)]*)\\)").matcher(line);
        List<String> out = new ArrayList<>();
        if (m.find()) {
            for (String s : m.group(1).split(",")) out.add(s.trim().replace("`", "").replace("\"", ""));
        }
        return out;
    }

    private static List<String> splitTop(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (char ch : body.toCharArray()) {
            if (ch == '(') depth++;
            if (ch == ')') depth--;
            if (ch == ',' && depth == 0) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else cur.append(ch);
        }
        if (cur.length() > 0) parts.add(cur.toString());
        return parts;
    }

    // ───────────────────────── output helpers ─────────────────────────

    private List<Relation> dedupe(List<Relation> in) {
        Map<String, Relation> map = new LinkedHashMap<>();
        for (Relation r : in) {
            if (!columns.containsKey(r.toTable()) || !columns.containsKey(r.fromTable())) continue;
            map.putIfAbsent(r.fromTable() + "." + r.fromColumn() + ">" + r.toTable(), r);
        }
        List<Relation> out = new ArrayList<>();
        for (Relation r : map.values()) {
            String label = r.toTable() + " 1 ─ N " + r.fromTable();
            if ("ONE_TO_ONE".equals(r.type())) label = r.toTable() + " 1 ─ 1 " + r.fromTable();
            out.add(new Relation(r.fromTable(), r.toTable(), r.fromColumn(), r.toColumn(), r.type(), r.cardinality(),
                    r.viaField(), r.joinTable(), label));
        }
        return out;
    }

    private String describe(String table, String entityId, List<Column> cols) {
        StringBuilder sb = new StringBuilder();
        ClassInfo e = entityId == null ? null : byId.get(entityId);
        if (e != null) sb.append("Stores ").append(e.name()).append(" records. ");
        else sb.append("Table `").append(table).append("`. ");
        sb.append(cols.size()).append(" column").append(cols.size() == 1 ? "" : "s");
        List<String> pk = cols.stream().filter(Column::primaryKey).map(Column::name).toList();
        if (!pk.isEmpty()) sb.append(", primary key ").append(String.join("+", pk));
        List<String> out = relations.stream().filter(r -> r.fromTable().equals(table)).map(Relation::toTable)
                .distinct().toList();
        if (!out.isEmpty()) sb.append(", references ").append(String.join(", ", out));
        List<String> in = relations.stream().filter(r -> r.toTable().equals(table)).map(Relation::fromTable)
                .distinct().toList();
        if (!in.isEmpty()) sb.append(", referenced by ").append(String.join(", ", in));
        return sb.append('.').toString();
    }

    public static String joinNames(Collection<String> c) { return c.stream().collect(Collectors.joining(", ")); }
}
