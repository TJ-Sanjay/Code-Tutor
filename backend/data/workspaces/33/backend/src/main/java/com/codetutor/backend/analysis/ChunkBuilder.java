package com.codetutor.backend.analysis;

import com.codetutor.backend.analysis.model.AnalysisModel.*;
import com.codetutor.backend.explain.Describer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Creates the semantic chunks that feed the vector index. Chunks follow the design document: one per method
 * ("owner.ownercontroller.processfindform"), one per class ("owner.ownercontroller"), one per module layer
 * ("owner.controller") and one per module, plus endpoint / table / security overviews and windows of
 * non-Java files (configuration, SQL, README, front-end code).
 */
public class ChunkBuilder {

    private static final int MAX_CHUNKS = 6000;
    private static final int MAX_METHOD_LINES = 90;
    private static final int WINDOW = 60;

    public List<Chunk> build(Path root, Knowledge k, Describer d, List<FileInfo> files) {
        List<Chunk> out = new ArrayList<>();
        Map<String, List<String>> cache = new HashMap<>();
        Set<String> ids = new HashSet<>();
        Result r = k.result;

        // overview chunks
        out.add(chunk(ids, "overview", "overview", "project", "Project overview", null, 1, 1,
                r.overview().summary() + "\n" + String.join("\n", r.overview().highlights()), null, null));
        for (ModuleInfo m : r.modules()) {
            out.add(chunk(ids, "module." + m.name(), "module", null, m.name() + " module", null, 1, 1,
                    m.description() + "\nClasses: " + m.layers().entrySet().stream()
                            .map(e -> e.getKey() + ": " + String.join(", ", e.getValue())).collect(Collectors.joining("; ")),
                    null, null, m.name()));
            for (var l : m.layers().entrySet()) {
                String text = l.getValue().stream().map(n -> {
                    ClassInfo c = k.classesByName.getOrDefault(n.toLowerCase(Locale.ROOT), List.of()).stream()
                            .filter(x -> x.module().equals(m.name())).findFirst().orElse(null);
                    return c == null ? n : n + " — " + d.classSummary(c);
                }).collect(Collectors.joining("\n"));
                out.add(chunk(ids, m.name() + "." + l.getKey().toLowerCase(Locale.ROOT), "layer", l.getKey(),
                        m.name() + " " + l.getKey().toLowerCase(Locale.ROOT) + " layer", null, 1, 1, text, null, null, m.name()));
            }
        }

        for (ClassInfo c : r.classes()) {
            if (c.test() || out.size() >= MAX_CHUNKS) continue;
            List<String> lines = lines(root, c.file(), cache);
            String base = c.module() + "." + c.name().toLowerCase(Locale.ROOT);
            // class chunk
            StringBuilder cs = new StringBuilder();
            cs.append(header(c, null, d)).append('\n');
            cs.append(c.annotations().stream().map(Ann::render).collect(Collectors.joining(" "))).append('\n');
            cs.append(c.kind().toLowerCase(Locale.ROOT)).append(' ').append(c.name());
            if (c.extendsType() != null) cs.append(" extends ").append(c.extendsType());
            if (!c.implementsTypes().isEmpty()) cs.append(" implements ").append(String.join(", ", c.implementsTypes()));
            cs.append('\n');
            for (FieldInfo f : c.fields()) cs.append("  field ").append(f.type()).append(' ').append(f.name()).append('\n');
            for (MethodInfo m : c.methods()) cs.append("  ").append(m.signature()).append('\n');
            out.add(chunk(ids, base, "class", c.layer(), c.name(), c.file(), c.line(), Math.min(c.endLine(), c.line() + 40),
                    cs.toString(), c.id(), null, c.module()));
            // method chunks
            for (MethodInfo m : c.methods()) {
                if (out.size() >= MAX_CHUNKS) break;
                int end = Math.min(m.endLine(), m.line() + MAX_METHOD_LINES);
                String code = slice(lines, m.line(), end);
                if (code.isBlank()) continue;
                String text = header(c, m, d) + "\n" + code;
                out.add(chunk(ids, base + "." + m.name().toLowerCase(Locale.ROOT), "method", c.layer(),
                        c.name() + "." + m.name(), c.file(), m.line(), m.endLine(), text, c.id(), m.id(), c.module()));
            }
        }

        for (EndpointInfo e : r.endpoints()) {
            ClassInfo c = k.classById.get(e.controller());
            MethodInfo m = k.methodById.get(e.handler());
            String text = "Endpoint " + e.httpMethod() + " " + e.fullPath() + "\nHandled by " + (c == null ? "" : c.name()) + "."
                    + (m == null ? "" : m.name()) + "\nAccess: " + d.accessLabel(e) + "\nInputs: "
                    + e.params().stream().map(p -> p.name() + " (" + p.source() + ")").collect(Collectors.joining(", "))
                    + "\nReturns: " + (e.viewResponse() ? "HTML view" : e.returns());
            out.add(chunk(ids, "endpoint." + (c == null ? "x" : c.name().toLowerCase(Locale.ROOT)) + "." + (m == null ? "" : m.name().toLowerCase(Locale.ROOT)),
                    "endpoint", "CONTROLLER", e.httpMethod() + " " + e.path(), e.file(), e.line(), e.line(), text,
                    e.controller(), e.handler(), c == null ? null : c.module()));
        }
        if (r.schema() != null) {
            for (Table t : r.schema().tables()) {
                String text = "Table " + t.name() + " (" + t.source() + ")\n" + t.description() + "\n"
                        + t.columns().stream().map(col -> "  " + col.name() + " " + col.type() + (col.primaryKey() ? " PRIMARY KEY" : "")
                        + (col.foreignKey() != null ? " REFERENCES " + col.foreignKey() : "") + (!col.nullable() ? " NOT NULL" : ""))
                        .collect(Collectors.joining("\n"));
                out.add(chunk(ids, "table." + t.name(), "table", "ENTITY", "table " + t.name(), t.file(), t.line(), t.line(), text,
                        t.entity(), null));
            }
        }
        Security s = r.security();
        if (s != null && s.enabled()) {
            String text = s.summary() + "\nFilter chain: " + s.filters().stream().map(f -> f.order() + ". " + f.name() + " — " + f.description())
                    .collect(Collectors.joining("\n")) + "\nRules: " + s.rules().stream()
                    .map(x -> x.pattern() + " → " + x.access()).collect(Collectors.joining("; ")) + "\n" + String.join("\n", s.notes());
            out.add(chunk(ids, "security.overview", "security", "SECURITY", "security configuration", null, 1, 1, text, s.configClass(), null));
        }

        // non-Java files
        Set<String> javaFiles = r.classes().stream().map(ClassInfo::file).collect(Collectors.toSet());
        for (FileInfo f : files) {
            if (out.size() >= MAX_CHUNKS) break;
            if (javaFiles.contains(f.path()) || f.size() > 200_000 || !textual(f.path())) continue;
            if (f.path().endsWith("package-lock.json") || f.path().endsWith(".min.js")) continue;
            List<String> lines = lines(root, f.path(), cache);
            if (lines.isEmpty()) continue;
            int step = WINDOW;
            int maxLines = Math.min(lines.size(), 360);
            for (int start = 1; start <= maxLines; start += step) {
                int end = Math.min(maxLines, start + step - 1);
                String code = slice(lines, start, end);
                if (code.isBlank()) continue;
                String name = f.path().substring(f.path().lastIndexOf('/') + 1);
                String text = "// File: " + f.path() + " (lines " + start + "-" + end + ")\n// " + (f.summary() == null ? "" : f.summary())
                        + "\n" + code;
                out.add(chunk(ids, "file." + f.path().toLowerCase(Locale.ROOT) + "." + start, "file", f.layer(), name,
                        f.path(), start, end, text, null, null, f.module()));
            }
        }
        return out;
    }

    private String header(ClassInfo c, MethodInfo m, Describer d) {
        String loc = c.file() + (m == null ? ":" + c.line() : ":" + m.line());
        String sum = m == null ? d.classSummary(c) : d.methodSummary(c, m);
        return "// " + c.module() + "." + c.name() + (m == null ? "" : "." + m.name()) + "  [" + c.layer() + " · " + c.module()
                + "]  " + loc + "\n// " + sum;
    }

    private Chunk chunk(Set<String> ids, String id, String type, String layer, String name, String file, int start, int end,
                        String text, String classId, String methodId) {
        return chunk(ids, id, type, layer, name, file, start, end, text, classId, methodId, null);
    }

    private Chunk chunk(Set<String> ids, String id, String type, String layer, String name, String file, int start, int end,
                        String text, String classId, String methodId, String module) {
        String unique = id;
        int n = 2;
        while (!ids.add(unique)) unique = id + "#" + n++;
        return new Chunk(unique, type, module, layer, name, file, start, end, text, classId, methodId);
    }

    private static String slice(List<String> lines, int from, int to) {
        if (lines.isEmpty()) return "";
        int a = Math.max(1, from);
        int b = Math.min(lines.size(), to);
        if (a > b) return "";
        return String.join("\n", lines.subList(a - 1, b));
    }

    private List<String> lines(Path root, String rel, Map<String, List<String>> cache) {
        return cache.computeIfAbsent(rel, p -> {
            try {
                return Files.readAllLines(root.resolve(p));
            } catch (IOException | RuntimeException e) {
                try {
                    return Arrays.asList(new String(Files.readAllBytes(root.resolve(p)), java.nio.charset.StandardCharsets.ISO_8859_1).split("\n", -1));
                } catch (IOException | RuntimeException e2) {
                    return List.of();
                }
            }
        });
    }

    static boolean textual(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        for (String ext : List.of(".java", ".js", ".jsx", ".ts", ".tsx", ".xml", ".yml", ".yaml", ".properties", ".sql", ".md",
                ".json", ".html", ".css", ".scss", ".py", ".sh", ".txt", ".gradle", ".kts", ".toml", ".conf", ".cfg")) {
            if (p.endsWith(ext)) return true;
        }
        String name = p.substring(p.lastIndexOf('/') + 1);
        return name.equals("dockerfile") || name.equals("makefile") || name.equals("jenkinsfile");
    }
}
