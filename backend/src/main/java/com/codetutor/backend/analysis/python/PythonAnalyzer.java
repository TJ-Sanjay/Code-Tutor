package com.codetutor.backend.analysis.python;

import com.codetutor.backend.analysis.model.AnalysisModel.*;
import com.codetutor.backend.analysis.python.PyModel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyses the Python part of a repository (Django first; Flask / FastAPI routes are recognised too) and produces the
 * same language-neutral model the Java analysis produces: classes with layers and modules, fields, methods with
 * resolved calls, dependencies, endpoints, the database schema (Django models) and the security configuration.
 */
public class PythonAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(PythonAnalyzer.class);

    public record Output(List<ClassInfo> classes, List<EndpointInfo> endpoints, Schema schema, Security security,
                         String basePackage, List<String> frameworks, List<String> databases, List<String> tools,
                         List<String> errors) {
        public static Output empty() {
            return new Output(List.of(), List.of(), null, null, null, List.of(), List.of(), List.of(), List.of());
        }
    }

    static final Set<String> APP_MARKERS = Set.of("models.py", "views.py", "urls.py", "apps.py", "settings.py", "wsgi.py",
            "admin.py", "serializers.py", "forms.py");
    static final Set<String> MODEL_BASES = Set.of("Model", "AbstractUser", "AbstractBaseUser", "AbstractBaseSession");
    static final Set<String> MANAGER_BASES = Set.of("Manager", "BaseManager", "QuerySet", "UserManager", "BaseUserManager");
    static final Set<String> ATTR_KEYS = Set.of("queryset", "model", "serializer_class", "form_class", "template_name",
            "permission_classes", "authentication_classes", "permission_required", "lookup_field", "http_method_names",
            "fields", "filterset_class", "pagination_class", "success_url", "context_object_name", "default_auto_field");
    private static final Pattern CALL = Pattern.compile("(?<![\\w.])([A-Za-z_]\\w*(?:\\s*\\.\\s*[A-Za-z_]\\w*)*)\\s*\\(");
    private static final Pattern SUPER_CALL = Pattern.compile("\\bsuper\\s*\\([^)]*\\)\\s*\\.\\s*([A-Za-z_]\\w*)\\s*\\(");
    private static final Pattern SHORTCUT = Pattern.compile("\\b(get_object_or_404|get_list_or_404)\\s*\\(\\s*([A-Za-z_][\\w.]*)");
    private static final Pattern FOR_OBJECTS = Pattern.compile("^for\\s+(\\w+)\\s+in\\s+([A-Za-z_][\\w.]*)\\.objects\\b");
    private static final Set<String> NOT_CALLS = Set.of("if", "while", "for", "print", "len", "str", "int", "float", "bool", "list",
            "dict", "set", "tuple", "isinstance", "getattr", "setattr", "hasattr", "range", "enumerate", "zip", "sorted", "super",
            "return", "not", "and", "or", "in", "is", "lambda", "type", "max", "min", "sum", "any", "all", "open", "repr", "map",
            "filter", "round", "abs", "iter", "next", "format", "id", "hash", "vars", "dir", "except", "assert", "raise", "with");

    // ───────────────────────── state ─────────────────────────

    Path root;
    Set<String> allPaths = Set.of();
    final Map<String, PF> files = new LinkedHashMap<>();
    final Map<String, PF> byDotted = new HashMap<>();
    final Map<String, Cx> classesById = new LinkedHashMap<>();
    final Map<String, List<Cx>> classesByName = new HashMap<>();
    final List<Cx> allCx = new ArrayList<>();
    final List<String> errors = new ArrayList<>();
    final Map<Cx, Map<String, PM>> methodIndex = new HashMap<>();
    boolean django;
    String framework = "python";
    DjangoSupport dj;

    // ───────────────────────── entry point ─────────────────────────

    public Output analyze(Path root, List<String> pyPaths, Set<String> allPaths, long maxFileBytes) {
        this.root = root;
        this.allPaths = allPaths;
        if (pyPaths.isEmpty()) return Output.empty();

        // 1. parse
        for (String rel : pyPaths) {
            if (isMigration(rel)) continue;
            try {
                Path p = root.resolve(rel);
                if (Files.size(p) > maxFileBytes) continue;
                String src = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                PF pf = new PF();
                pf.path = rel;
                pf.mod = PyParser.parse(rel, src);
                files.put(rel, pf);
            } catch (IOException | RuntimeException e) {
                errors.add(rel + ": " + e.getMessage());
                log.debug("Python parse failure in {}", rel, e);
            }
        }
        if (files.isEmpty()) return Output.empty();

        // 2. names, imports, django detection
        for (PF pf : files.values()) {
            pf.dotted = dottedOf(pf.path);
            pf.app = appOf(pf.path);
            pf.test = isTestPath(pf.path);
            byDotted.putIfAbsent(pf.dotted, pf);
            for (PyParser.Imp imp : pf.mod.imports) {
                if (imp.star()) pf.stars.add(imp);
                else if (imp.alias() != null) pf.bindings.putIfAbsent(imp.alias(), imp);
                String m = imp.module() == null ? "" : imp.module();
                if (m.equals("django") || m.startsWith("django.") || m.equals("rest_framework") || m.startsWith("rest_framework")) django = true;
            }
        }
        if (files.keySet().stream().anyMatch(p -> p.equals("manage.py") || p.endsWith("/manage.py"))) django = true;
        framework = django ? "django" : "python";
        dj = new DjangoSupport(this);

        // 3. classes + module classes
        for (PF pf : files.values()) createClasses(pf);
        for (Cx c : allCx) resolveBases(c);
        if (django) dj.parseSettings();
        for (Cx c : allCx) c.layer = detectLayer(c);
        if (django) dj.reclassifyMiddleware();

        // 4. members, calls, dependencies
        for (Cx c : allCx) buildMembers(c);
        for (Cx c : allCx) linkManagers(c);
        for (Cx c : allCx) fillMetaFields(c);
        for (Cx c : allCx) for (PM m : new ArrayList<>(c.methods)) {
            try {
                resolveCalls(m);
            } catch (RuntimeException e) {
                errors.add(c.pf.path + ": call resolution failed (" + e.getMessage() + ")");
            }
        }
        for (Cx c : allCx) computeDeps(c);

        // 5. endpoints, schema, security
        List<EndpointInfo> endpoints = new ArrayList<>();
        Schema schema = null;
        Security security = null;
        if (django) {
            endpoints.addAll(dj.endpoints());
            schema = dj.schema();
        }
        endpoints.addAll(dj.decoratorRoutes());
        for (Cx c : allCx) computeDeps(c); // synthetic handlers added links to models / serializers
        security = dj.security(endpoints);

        // 6. materialise
        List<ClassInfo> out = new ArrayList<>();
        for (Cx c : allCx) out.add(toInfo(c));
        String basePackage = dj.projectPackage();
        log.info("Python analysis complete: {} classes, {} endpoints, {} errors", out.size(), endpoints.size(), errors.size());
        return new Output(out, endpoints, schema, security, basePackage, dj.frameworks(), dj.databases(), dj.tools(), errors);
    }

    // ───────────────────────── naming ─────────────────────────

    static boolean isMigration(String rel) {
        return rel.contains("/migrations/") || rel.startsWith("migrations/");
    }

    static boolean isTestPath(String rel) {
        String l = rel.toLowerCase(Locale.ROOT);
        String name = l.substring(l.lastIndexOf('/') + 1);
        return l.contains("/tests/") || l.startsWith("tests/") || l.contains("/test/") || name.startsWith("test_")
                || name.endsWith("_test.py") || name.endsWith("_tests.py") || name.equals("tests.py") || name.equals("conftest.py");
    }

    boolean isPackageDir(String dir) {
        if (allPaths.contains(dir + "/__init__.py")) return true;
        for (String m : APP_MARKERS) if (allPaths.contains(dir + "/" + m)) return true;
        return false;
    }

    private boolean isAppDir(String dir) {
        for (String m : APP_MARKERS) if (allPaths.contains(dir + "/" + m)) return true;
        return false;
    }

    String dottedOf(String rel) {
        String noExt = rel.substring(0, rel.length() - 3);
        String[] seg = noExt.split("/");
        LinkedList<String> parts = new LinkedList<>();
        parts.add(seg[seg.length - 1]);
        for (int i = seg.length - 2; i >= 0; i--) {
            String dir = String.join("/", Arrays.copyOfRange(seg, 0, i + 1));
            if (isPackageDir(dir)) parts.addFirst(seg[i]);
            else break;
        }
        if (parts.getLast().equals("__init__")) {
            parts.removeLast();
            if (parts.isEmpty()) parts.add(seg.length > 1 ? seg[seg.length - 2] : "root");
        }
        return String.join(".", parts);
    }

    String appOf(String rel) {
        String[] seg = rel.split("/");
        String app = null;
        String outer = null;
        for (int i = seg.length - 2; i >= 0; i--) {
            String dir = String.join("/", Arrays.copyOfRange(seg, 0, i + 1));
            if (isAppDir(dir)) app = seg[i];
            if (isPackageDir(dir)) outer = seg[i];
            else break;
        }
        if (app != null) return app;
        if (outer != null) return outer;
        return "core";
    }

    // ───────────────────────── classes ─────────────────────────

    private void createClasses(PF pf) {
        String pkg = pf.dotted.contains(".") ? pf.dotted.substring(0, pf.dotted.lastIndexOf('.')) : "";
        createModuleClass(pf, pkg);
        for (PyParser.Cls pc : pf.mod.classes) {
            Cx c = new Cx();
            c.pf = pf;
            c.cls = pc;
            c.name = pc.name;
            c.id = pf.dotted + "." + pc.name;
            c.pkg = pkg;
            c.module = pf.app;
            c.line = firstLine(pc);
            c.endLine = pc.endLine;
            c.doc = pc.doc;
            c.test = pf.test;
            c.baseTexts.addAll(pc.bases);
            c.attrs.put("framework", framework);
            for (String d : pc.decorators) c.anns.add(annOf(d));
            if (classesById.containsKey(c.id)) c.id = c.id + "@" + pc.line;
            pf.classes.add(c);
            register(c);
        }
    }

    /** Groups the module-level functions of a file (and special files such as urls / settings) into one "module class". */
    private void createModuleClass(PF pf, String pkg) {
        String stem = pf.stem();
        boolean special = Set.of("urls", "settings", "manage", "wsgi", "asgi").contains(stem) || pf.path.contains("/settings/");
        if (!pf.mod.functions.isEmpty() || (special && django)) {
            Cx m = new Cx();
            m.pf = pf;
            m.moduleClass = true;
            m.kind = "MODULE";
            m.id = pf.dotted;
            m.pkg = pkg;
            String[] parts = pf.dotted.split("\\.");
            m.name = parts.length >= 2 ? parts[parts.length - 2] + "." + parts[parts.length - 1] : pf.dotted;
            m.module = pf.app;
            m.line = 1;
            m.endLine = Math.max(1, pf.mod.lines);
            m.doc = pf.mod.doc;
            m.test = pf.test;
            m.attrs.put("framework", framework);
            m.attrs.put("moduleFile", "true");
            if (classesById.containsKey(m.id)) m.id = m.id + "#module";
            pf.moduleCx = m;
            register(m);
            for (PyParser.Func f : pf.mod.functions) addMethod(m, f);
        }
    }

    private static int firstLine(PyParser.Cls c) { return c.line; }

    private void register(Cx c) {
        classesById.put(c.id, c);
        allCx.add(c);
        classesByName.computeIfAbsent(c.name, k -> new ArrayList<>()).add(c);
    }

    private PM addMethod(Cx owner, PyParser.Func f) {
        PM m = new PM();
        m.fn = f;
        m.owner = owner;
        m.name = f.name;
        m.line = f.line;
        m.endLine = Math.max(f.endLine, f.line);
        m.loc = m.endLine - m.line + 1;
        m.doc = f.doc;
        StringBuilder ps = new StringBuilder();
        for (PyParser.Param p : f.params) {
            if (p.name().equals("self") || p.name().equals("cls")) continue;
            if (ps.length() > 0) ps.append(",");
            ps.append(p.name());
        }
        String id = owner.id + "#" + f.name + "(" + ps + ")";
        if (methodIndex.computeIfAbsent(owner, k -> new LinkedHashMap<>()).containsKey(f.name)) id = id + "@" + f.line;
        m.id = id;
        for (String d : f.decorators) m.anns.add(annOf(d));
        owner.methods.add(m);
        methodIndex.get(owner).putIfAbsent(f.name, m);
        return m;
    }

    static Ann annOf(String deco) {
        String d = deco.strip();
        String[] call = PyParser.call(d);
        if (call == null) return new Ann(lastSeg(d.contains("(") ? d.substring(0, d.indexOf('(')) : d), Map.of());
        Map<String, String> args = new LinkedHashMap<>();
        List<String> pos = new ArrayList<>();
        Map<String, String> kw = new LinkedHashMap<>();
        PyParser.args(call[1], pos, kw);
        if (!pos.isEmpty()) args.put("value", pos.get(0));
        args.putAll(kw);
        return new Ann(lastSeg(call[0]), args);
    }

    static String lastSeg(String s) {
        int i = s.lastIndexOf('.');
        return i < 0 ? s.strip() : s.substring(i + 1).strip();
    }

    // ───────────────────────── bases and layers ─────────────────────────

    private void resolveBases(Cx c) {
        if (c.cls == null) return;
        for (String b : c.cls.bases) {
            Cx t = classByExpr(c.pf, b, true);
            if (t != null && t != c) c.bases.add(t);
        }
    }

    Set<String> allBases(Cx c) {
        if (c.allBaseNames != null) return c.allBaseNames;
        c.allBaseNames = new HashSet<>();
        Set<Cx> seen = new HashSet<>();
        collectBases(c, c.allBaseNames, seen);
        return c.allBaseNames;
    }

    private void collectBases(Cx c, Set<String> out, Set<Cx> seen) {
        if (!seen.add(c)) return;
        for (String b : c.baseTexts) out.add(lastSeg(b));
        for (Cx b : c.bases) collectBases(b, out, seen);
    }

    private static boolean any(Set<String> names, Set<String> of) {
        for (String n : names) if (of.contains(n)) return true;
        return false;
    }

    private static boolean anySuffix(Set<String> names, String... suffixes) {
        for (String n : names) for (String s : suffixes) if (n.endsWith(s)) return true;
        return false;
    }

    private String detectLayer(Cx c) {
        if (c.test) return "TEST";
        if (c.moduleClass) return stemLayer(c.pf);
        Set<String> b = allBases(c);
        if (b.stream().anyMatch(n -> n.endsWith("TestCase") || n.equals("TransactionTestCase"))) { c.test = true; return "TEST"; }
        if (any(b, MODEL_BASES)) { c.attrs.put("djangoKind", "model"); return "ENTITY"; }
        if (any(b, MANAGER_BASES)) { c.attrs.put("djangoKind", "manager"); return "REPOSITORY"; }
        if (b.contains("TextChoices") || b.contains("IntegerChoices") || b.contains("Enum") || b.contains("IntEnum") || b.contains("StrEnum")) {
            c.kind = "ENUM";
            return "MODEL";
        }
        if (anySuffix(b, "ViewSet")) { c.attrs.put("djangoKind", "viewset"); return "CONTROLLER"; }
        if (anySuffix(b, "View") || b.contains("APIView")) { c.attrs.put("djangoKind", "view"); return "CONTROLLER"; }
        if (anySuffix(b, "Serializer")) { c.attrs.put("djangoKind", "serializer"); return "DTO"; }
        if (anySuffix(b, "Form", "FilterSet") || b.contains("BaseModel") || c.anns.stream().anyMatch(a -> a.name().equals("dataclass"))) {
            c.attrs.put("djangoKind", b.contains("BaseModel") ? "schema" : "form");
            return "DTO";
        }
        if (any(b, Set.of("ModelAdmin", "StackedInline", "TabularInline", "AdminSite", "AppConfig"))) {
            c.attrs.put("djangoKind", b.contains("AppConfig") ? "appconfig" : "admin");
            return "CONFIG";
        }
        if (anySuffix(b, "Permission") || b.contains("BasePermission")) { c.attrs.put("djangoKind", "permission"); return "SECURITY"; }
        if (anySuffix(b, "Authentication", "Backend")) { c.attrs.put("djangoKind", "authentication"); return "SECURITY"; }
        if (b.contains("MiddlewareMixin")) { c.attrs.put("djangoKind", "middleware"); return "COMPONENT"; }
        if (b.contains("BaseCommand")) { c.attrs.put("djangoKind", "command"); return "COMPONENT"; }
        if (b.stream().anyMatch(n -> n.equals("Exception") || n.endsWith("Exception") || n.endsWith("Error"))) return "EXCEPTION";
        if (c.name.endsWith("Service") || c.name.endsWith("Services")) return "SERVICE";
        return stemLayer(c.pf);
    }

    static String stemLayer(PF pf) {
        String p = pf.path.toLowerCase(Locale.ROOT);
        String stem = pf.stem().toLowerCase(Locale.ROOT);
        if (isTestPath(pf.path)) return "TEST";
        if (Set.of("manage", "wsgi", "asgi").contains(stem)) return "APPLICATION";
        if (p.contains("/management/commands/")) return "COMPONENT";
        if (p.contains("/settings/") || Set.of("settings", "urls", "admin", "apps", "conf", "config", "celery").contains(stem)) return "CONFIG";
        if (p.contains("/views/") || Set.of("views", "viewsets", "api", "endpoints", "routes", "routers", "resources").contains(stem)) return "CONTROLLER";
        if (p.contains("/services/") || Set.of("services", "service", "usecases", "use_cases", "business", "logic", "interactors").contains(stem)) return "SERVICE";
        if (Set.of("repositories", "repository", "selectors", "queries", "dao", "managers").contains(stem)) return "REPOSITORY";
        if (p.contains("/serializers/") || Set.of("serializers", "forms", "filters", "dto", "dtos", "schemas").contains(stem)) return "DTO";
        if (Set.of("models", "entities").contains(stem) || p.contains("/models/")) return "ENTITY";
        if (Set.of("permissions", "auth", "authentication", "backends", "security", "jwt", "tokens").contains(stem)) return "SECURITY";
        if (Set.of("middleware", "middlewares", "signals", "receivers", "tasks", "consumers", "handlers").contains(stem)) return "COMPONENT";
        if (Set.of("utils", "helpers", "common", "tools", "shortcuts", "mixins", "decorators", "validators", "util").contains(stem)) return "UTIL";
        if (Set.of("exceptions", "errors").contains(stem)) return "EXCEPTION";
        if (Set.of("constants", "enums", "choices").contains(stem)) return "MODEL";
        return "OTHER";
    }

    // ───────────────────────── members ─────────────────────────

    private void buildMembers(Cx c) {
        if (c.cls == null) {
            if (c.moduleClass) {
                for (Map.Entry<String, PyParser.Stmt> e : moduleAssignments(c.pf).entrySet()) {
                    String n = e.getKey();
                    if (n.equals("urlpatterns") || n.equals("INSTALLED_APPS") || n.equals("MIDDLEWARE")) {
                        c.fields.add(new FieldInfo(n, "list", e.getValue().line(), List.of(), false, null, true, false));
                    }
                }
            }
            c.imports = importsOf(c.pf);
            return;
        }
        PyParser.Cls pc = c.cls;
        for (PyParser.Func f : pc.methods) addMethod(c, f);
        for (PyParser.Cls n : pc.nested) {
            if (n.name.equals("Meta") || n.name.equals("Config") || n.name.equals("Params")) {
                for (Map.Entry<String, PyParser.Stmt> e : n.assignments().entrySet()) c.attrs.put("meta." + e.getKey(), e.getValue().text());
            }
        }
        for (Map.Entry<String, PyParser.Stmt> e : pc.assignments().entrySet()) {
            String name = e.getKey();
            String val = e.getValue().text();
            int line = e.getValue().line();
            if (ATTR_KEYS.contains(name)) c.attrs.put("py." + name, val);
            FieldInfo f = fieldOf(c, name, val, line);
            if (f != null) c.fields.add(f);
        }
        for (Map.Entry<String, String> e : pc.annotated().entrySet()) {
            Cx t = classByExpr(c.pf, simpleType(e.getValue()), false);
            c.fields.add(new FieldInfo(e.getKey(), e.getValue(), pc.line, List.of(), false, t == null ? null : t.id, false, false));
        }
        PM init = methodIndex.getOrDefault(c, Map.of()).get("__init__");
        if (init != null) initFields(c, init);
        c.imports = importsOf(c.pf);
    }

    private List<String> importsOf(PF pf) {
        List<String> out = new ArrayList<>();
        for (PyParser.Imp i : pf.mod.imports) {
            String m = (".".repeat(i.level())) + (i.module() == null ? "" : i.module());
            if (i.star()) out.add("from " + m + " import *");
            else if (i.name() != null) out.add("from " + m + " import " + i.name() + (i.alias() != null && !i.alias().equals(i.name()) ? " as " + i.alias() : ""));
            else out.add("import " + i.module() + (i.alias() != null && !i.alias().equals(i.module()) ? " as " + i.alias() : ""));
        }
        return out;
    }

    private Map<String, PyParser.Stmt> moduleAssignments(PF pf) {
        Map<String, PyParser.Stmt> out = new LinkedHashMap<>();
        for (PyParser.Stmt s : pf.mod.top) {
            PyParser.Assign a = PyParser.assign(s.text());
            if (a != null && a.value() != null) out.putIfAbsent(a.name(), s);
        }
        return out;
    }

    private static String simpleType(String ann) {
        String a = ann.strip().replaceAll("^(Optional|List|Set|Sequence|Iterable)\\[(.*)]$", "$2").replace("\"", "").replace("'", "");
        return a.split("[\\[|,\\s]")[0];
    }

    private static final Set<String> RELATION_FIELDS = Set.of("ForeignKey", "OneToOneField", "ManyToManyField");

    private FieldInfo fieldOf(Cx c, String name, String val, int line) {
        String[] call = PyParser.call(val);
        if (call != null) {
            String last = lastSeg(call[0]);
            boolean fieldCall = last.endsWith("Field") || RELATION_FIELDS.contains(last) || last.equals("Column")
                    || last.equals("relationship") || last.equals("GenericForeignKey");
            Map<String, String> args = new LinkedHashMap<>();
            List<String> pos = new ArrayList<>();
            Map<String, String> kw = new LinkedHashMap<>();
            PyParser.args(call[1], pos, kw);
            if (!pos.isEmpty()) args.put("value", pos.get(0));
            args.putAll(kw);
            if (fieldCall) {
                Ann a = new Ann(last, args);
                if (RELATION_FIELDS.contains(last)) {
                    String ref = kw.getOrDefault("to", pos.isEmpty() ? null : pos.get(0));
                    Cx target = ref == null ? null : dj.resolveModelRef(c, ref);
                    String tname = target != null ? target.name : (ref == null ? last : cleanRef(ref));
                    return new FieldInfo(name, tname, line, List.of(a), false, target == null ? null : target.id, false, false);
                }
                return new FieldInfo(name, last, line, List.of(a), false, null, false, false);
            }
            Cx t = classByExpr(c.pf, call[0], false);
            if (t != null) return new FieldInfo(name, t.name, line, List.of(), false, t.id, true, false);
            return new FieldInfo(name, last + "()", line, List.of(), false, null, true, false);
        }
        String v = val.strip();
        if (v.matches("[A-Za-z_][\\w.]*")) {
            Cx t = classByExpr(c.pf, v, false);
            if (t != null) return new FieldInfo(name, t.name, line, List.of(), false, t.id, true, false);
        }
        return new FieldInfo(name, literalKind(v), line, List.of(), false, null, true, name.equals(name.toUpperCase(Locale.ROOT)));
    }

    static String cleanRef(String ref) {
        String r = ref.strip().replace("\"", "").replace("'", "");
        return lastSeg(r);
    }

    private static String literalKind(String v) {
        if (v.startsWith("[") || v.startsWith("(")) return "list";
        if (v.startsWith("{")) return "dict";
        if (v.equals("True") || v.equals("False")) return "bool";
        if (v.matches("-?\\d+")) return "int";
        if (v.matches("-?\\d*\\.\\d+")) return "float";
        if (PyParser.isString(v)) return "str";
        return "value";
    }

    private void initFields(Cx c, PM init) {
        Map<String, String> paramTypes = new HashMap<>();
        for (PyParser.Param p : init.fn.params) if (p.annotation() != null) paramTypes.put(p.name(), simpleType(p.annotation()));
        for (PyParser.Stmt st : init.fn.body) {
            Matcher m = Pattern.compile("^self\\.(\\w+)\\s*(?::\\s*[^=]+)?=\\s*(.+)$", Pattern.DOTALL).matcher(st.text());
            if (!m.matches()) continue;
            String name = m.group(1);
            String rhs = m.group(2).strip();
            Cx t = null;
            String type = null;
            boolean injected = false;
            if (rhs.matches("[A-Za-z_]\\w*") && paramTypes.containsKey(rhs)) {
                type = paramTypes.get(rhs);
                t = classByExpr(c.pf, type, false);
                injected = true;
            } else {
                String[] call = PyParser.call(rhs);
                if (call != null) {
                    t = classByExpr(c.pf, call[0], false);
                    type = lastSeg(call[0]);
                    injected = t != null;
                }
            }
            if (type == null) continue;
            final String fname = name;
            c.fields.removeIf(f -> f.name().equals(fname));
            c.fields.add(new FieldInfo(name, t != null ? t.name : type, st.line(), List.of(), injected && t != null,
                    t == null ? null : t.id, false, false));
        }
    }

    private void linkManagers(Cx c) {
        if (c.cls == null) return;
        for (FieldInfo f : c.fields) {
            if (f.resolvedClass() == null) continue;
            Cx t = classesById.get(f.resolvedClass());
            if (t != null && "REPOSITORY".equals(t.layer) && "manager".equals(t.attrs.get("djangoKind")) && !c.managers.contains(t)) {
                c.managers.add(t);
                if ("ENTITY".equals(c.layer)) t.attrs.putIfAbsent("entityType", c.name);
            }
        }
        // objects = SomeQuerySet.as_manager()
        for (Map.Entry<String, PyParser.Stmt> e : c.cls.assignments().entrySet()) {
            String v = e.getValue().text();
            Matcher m = Pattern.compile("^([A-Za-z_][\\w.]*)\\.(?:as_manager|from_queryset)\\(").matcher(v);
            if (m.find()) {
                Cx t = classByExpr(c.pf, m.group(1), false);
                if (t != null && !c.managers.contains(t)) {
                    c.managers.add(t);
                    if ("ENTITY".equals(c.layer)) t.attrs.putIfAbsent("entityType", c.name);
                }
            }
        }
    }

    /** Serializers and forms list their fields in Meta.fields; expose those names as the class's fields. */
    private void fillMetaFields(Cx c) {
        String kind = c.attrs.get("djangoKind");
        if (!"serializer".equals(kind) && !"form".equals(kind)) return;
        String fields = attr(c, "meta.fields");
        if (fields == null) return;
        String mm = attr(c, "meta.model");
        Cx model = mm == null ? null : classByExpr(c.pf, mm, true);
        List<String> names = new ArrayList<>();
        if (fields.contains("__all__")) {
            if (model != null) for (FieldInfo f : model.fields) if (!f.isStatic() && !f.annotations().isEmpty()) names.add(f.name());
        } else {
            names.addAll(PyParser.strings(fields));
        }
        for (String n : names) {
            if (c.fields.stream().anyMatch(f -> f.name().equals(n))) continue;
            FieldInfo mf = model == null ? null : findField(model, n);
            String type = mf == null ? "field" : mf.annotations().isEmpty() ? mf.type() : mf.annotations().get(0).name();
            c.fields.add(new FieldInfo(n, type, c.line, List.of(), false, null, false, false));
        }
    }

    // ───────────────────────── name resolution ─────────────────────────

    /** Finds the module file for a dotted name (exact, then unique suffix). */
    PF moduleByDotted(String dotted) {
        if (dotted == null || dotted.isEmpty()) return null;
        PF pf = byDotted.get(dotted);
        if (pf != null) return pf;
        PF found = null;
        for (Map.Entry<String, PF> e : byDotted.entrySet()) {
            if (e.getKey().endsWith("." + dotted)) {
                if (found != null) return null;
                found = e.getValue();
            }
        }
        return found;
    }

    /** Absolute dotted module an import refers to, taking relative imports into account. */
    String importBase(PF pf, PyParser.Imp imp) {
        String mod = imp.module() == null ? "" : imp.module();
        if (imp.level() == 0) return mod;
        List<String> pkg = new ArrayList<>(Arrays.asList(pf.dotted.split("\\.")));
        if (!pf.stem().equals("__init__") && !pkg.isEmpty()) pkg.remove(pkg.size() - 1);
        for (int i = 1; i < imp.level() && !pkg.isEmpty(); i++) pkg.remove(pkg.size() - 1);
        String base = String.join(".", pkg);
        if (mod.isEmpty()) return base;
        return base.isEmpty() ? mod : base + "." + mod;
    }

    /** Module that an alias (as in {@code from . import views}) points at, or null. */
    PF moduleOfAlias(PF ctx, String alias) {
        PyParser.Imp imp = ctx.bindings.get(alias);
        if (imp == null) return null;
        String base = importBase(ctx, imp);
        if (imp.name() == null) return moduleByDotted(imp.module());
        PF m = moduleByDotted(base.isEmpty() ? imp.name() : base + "." + imp.name());
        return m;
    }

    /** Resolves an expression like {@code Owner}, {@code models.Owner}, {@code app.models.Owner} to a project class. */
    Cx classByExpr(PF ctx, String expr, boolean allowGlobal) {
        if (expr == null) return null;
        String e = expr.strip().replace(" ", "");
        if (e.isEmpty()) return null;
        String[] parts = e.split("\\.");
        if (parts.length == 1) return classByName(ctx, parts[0], allowGlobal);
        // module alias + class
        PF mod = moduleOfAlias(ctx, parts[0]);
        int idx = 1;
        if (mod == null) {
            // fully dotted module path
            for (int i = parts.length - 1; i >= 1; i--) {
                mod = moduleByDotted(String.join(".", Arrays.copyOfRange(parts, 0, i)));
                if (mod != null) { idx = i; break; }
            }
        } else {
            while (idx < parts.length - 1) {
                PF next = moduleByDotted(mod.dotted + "." + parts[idx]);
                if (next == null) break;
                mod = next;
                idx++;
            }
        }
        if (mod != null && idx < parts.length) {
            for (Cx c : mod.classes) if (c.name.equals(parts[idx])) return c;
            Cx viaStar = classInModule(mod, parts[idx], new HashSet<>());
            if (viaStar != null) return viaStar;
        }
        return null;
    }

    private Cx classInModule(PF mod, String name, Set<PF> seen) {
        if (!seen.add(mod)) return null;
        for (Cx c : mod.classes) if (c.name.equals(name)) return c;
        PyParser.Imp imp = mod.bindings.get(name);
        if (imp != null && imp.name() != null) {
            PF target = moduleByDotted(importBase(mod, imp));
            if (target != null) {
                Cx c = classInModule(target, imp.name(), seen);
                if (c != null) return c;
            }
        }
        for (PyParser.Imp s : mod.stars) {
            PF target = moduleByDotted(importBase(mod, s));
            if (target != null) {
                Cx c = classInModule(target, name, seen);
                if (c != null) return c;
            }
        }
        return null;
    }

    Cx classByName(PF ctx, String name, boolean allowGlobal) {
        for (Cx c : ctx.classes) if (c.name.equals(name)) return c;
        PyParser.Imp imp = ctx.bindings.get(name);
        if (imp != null && imp.name() != null) {
            PF target = moduleByDotted(importBase(ctx, imp));
            if (target != null) {
                Cx c = classInModule(target, imp.name(), new HashSet<>());
                if (c != null) return c;
            }
        }
        for (PyParser.Imp s : ctx.stars) {
            PF target = moduleByDotted(importBase(ctx, s));
            if (target != null) {
                Cx c = classInModule(target, name, new HashSet<>());
                if (c != null) return c;
            }
        }
        if (allowGlobal) {
            List<Cx> l = classesByName.get(name);
            if (l != null) {
                Cx only = null;
                for (Cx c : l) if (!c.moduleClass) { if (only != null) return null; only = c; }
                return only;
            }
        }
        return null;
    }

    /** A function by (possibly dotted) name as seen from {@code ctx}. */
    PM functionByExpr(PF ctx, String expr) {
        if (expr == null) return null;
        String[] parts = expr.strip().replace(" ", "").split("\\.");
        if (parts.length == 1) {
            PM m = functionIn(ctx, parts[0]);
            if (m != null) return m;
            PyParser.Imp imp = ctx.bindings.get(parts[0]);
            if (imp != null && imp.name() != null) {
                PF target = moduleByDotted(importBase(ctx, imp));
                if (target != null) return functionIn(target, imp.name());
            }
            for (PyParser.Imp s : ctx.stars) {
                PF target = moduleByDotted(importBase(ctx, s));
                if (target != null) { PM x = functionIn(target, parts[0]); if (x != null) return x; }
            }
            return null;
        }
        PF mod = moduleOfAlias(ctx, parts[0]);
        int idx = 1;
        if (mod == null) {
            for (int i = parts.length - 1; i >= 1; i--) {
                mod = moduleByDotted(String.join(".", Arrays.copyOfRange(parts, 0, i)));
                if (mod != null) { idx = i; break; }
            }
        } else {
            while (idx < parts.length - 1) {
                PF next = moduleByDotted(mod.dotted + "." + parts[idx]);
                if (next == null) break;
                mod = next;
                idx++;
            }
        }
        if (mod != null && idx == parts.length - 1) return functionIn(mod, parts[idx]);
        return null;
    }

    private PM functionIn(PF mod, String name) {
        if (mod.moduleCx != null) {
            Map<String, PM> idx = methodIndex.get(mod.moduleCx);
            if (idx != null && idx.containsKey(name)) return idx.get(name);
            for (PM m : mod.moduleCx.methods) if (m.name.equals(name)) return m;
        }
        PyParser.Imp imp = mod.bindings.get(name);
        if (imp != null && imp.name() != null) {
            PF target = moduleByDotted(importBase(mod, imp));
            if (target != null && target != mod) return functionIn(target, imp.name());
        }
        return null;
    }

    PM findMethod(Cx c, String name) {
        return findMethod(c, name, new HashSet<>());
    }

    private PM findMethod(Cx c, String name, Set<Cx> seen) {
        if (c == null || !seen.add(c)) return null;
        Map<String, PM> idx = methodIndex.get(c);
        if (idx != null && idx.containsKey(name)) return idx.get(name);
        for (Cx b : c.bases) {
            PM m = findMethod(b, name, seen);
            if (m != null) return m;
        }
        return null;
    }

    FieldInfo findField(Cx c, String name) {
        return findField(c, name, new HashSet<>());
    }

    private FieldInfo findField(Cx c, String name, Set<Cx> seen) {
        if (c == null || !seen.add(c)) return null;
        for (FieldInfo f : c.fields) if (f.name().equals(name)) return f;
        for (Cx b : c.bases) {
            FieldInfo f = findField(b, name, seen);
            if (f != null) return f;
        }
        return null;
    }

    /** Class attribute (py.xxx) of a class or its project bases. */
    String attr(Cx c, String key) {
        return attr(c, key, new HashSet<>());
    }

    private String attr(Cx c, String key, Set<Cx> seen) {
        if (c == null || !seen.add(c)) return null;
        String v = c.attrs.get(key);
        if (v != null) return v;
        for (Cx b : c.bases) {
            v = attr(b, key, seen);
            if (v != null) return v;
        }
        return null;
    }

    // ───────────────────────── calls ─────────────────────────

    private static boolean isModel(Cx c) { return c != null && "ENTITY".equals(c.layer) && "model".equals(c.attrs.get("djangoKind")); }

    private Call callTo(PM target, int line) {
        return new Call(target.owner.id, target.owner.name, target.name, line, target.id, false);
    }

    private Call orm(Cx model, String op, int line) {
        return new Call(model.id, model.name, op, line, null, true);
    }

    private Cx inferType(PF pf, Cx owner, Map<String, Cx> vars, String value) {
        String v = value.strip();
        Matcher sc = SHORTCUT.matcher(v);
        if (sc.find()) return classByExpr(pf, sc.group(2), true);
        Matcher om = Pattern.compile("^([A-Za-z_][\\w.]*)\\.objects\\b").matcher(v);
        if (om.find() && !v.matches("(?s).*\\.(filter|all|exclude|order_by|values|annotate)\\(.*")) {
            Cx t = classByExpr(pf, om.group(1), true);
            if (isModel(t)) return t;
        }
        String[] call = PyParser.call(v);
        if (call != null) {
            String callee = call[0];
            if (callee.equals("self.get_serializer") || callee.equals("self.serializer_class")) {
                String sc2 = attr(owner, "py.serializer_class");
                return sc2 == null ? null : classByExpr(pf, sc2, true);
            }
            if (callee.equals("self.get_object")) {
                Cx m = dj == null ? null : dj.modelOfView(owner);
                return m;
            }
            Cx t = classByExpr(pf, callee, true);
            if (t != null && !t.moduleClass) return t;
        }
        if (v.matches("[A-Za-z_]\\w*") && vars.containsKey(v)) return vars.get(v);
        Matcher cm = Pattern.compile("^([A-Za-z_][\\w.]*)\\.objects\\.(?:get|create|first|last|get_or_create|update_or_create)\\(").matcher(v);
        if (cm.find()) {
            Cx t = classByExpr(pf, cm.group(1), true);
            if (isModel(t)) return t;
        }
        return null;
    }

    private void resolveCalls(PM m) {
        if (m.fn == null) return;
        Cx owner = m.owner;
        PF pf = owner.pf;
        Map<String, Cx> vars = new HashMap<>();
        for (PyParser.Param p : m.fn.params) {
            if (p.annotation() != null) {
                Cx t = classByExpr(pf, simpleType(p.annotation()), true);
                if (t != null && !t.moduleClass) vars.put(p.name(), t);
            }
        }
        for (PyParser.Stmt st : m.fn.body) {
            String t = st.text();
            Matcher fm = FOR_OBJECTS.matcher(t);
            if (fm.find()) {
                Cx c = classByExpr(pf, fm.group(2), true);
                if (isModel(c)) vars.put(fm.group(1), c);
            }
            PyParser.Assign a = PyParser.assign(t);
            if (a != null && a.value() != null) {
                Cx ty = inferType(pf, owner, vars, a.value());
                if (ty != null) vars.put(a.name(), ty);
            }
        }
        Set<String> seen = new HashSet<>();
        for (PyParser.Stmt st : m.fn.body) {
            String masked = PyParser.maskStrings(st.text());
            Matcher sc = SHORTCUT.matcher(masked);
            while (sc.find()) {
                Cx t = classByExpr(pf, sc.group(2), true);
                if (isModel(t)) add(m, seen, orm(t, sc.group(1).equals("get_list_or_404") ? "filter" : "get", lineOf(st, masked, sc.start())));
            }
            Matcher sup = SUPER_CALL.matcher(masked);
            while (sup.find()) {
                PM target = null;
                for (Cx b : owner.bases) { target = findMethod(b, sup.group(1)); if (target != null) break; }
                if (target != null) add(m, seen, callTo(target, lineOf(st, masked, sup.start())));
            }
            Matcher cm = CALL.matcher(masked);
            while (cm.find()) {
                String chain = cm.group(1).replaceAll("\\s+", "");
                String[] parts = chain.split("\\.");
                if (parts.length == 1 && NOT_CALLS.contains(parts[0])) continue;
                Call c = resolveCall(pf, owner, vars, parts, lineOf(st, masked, cm.start(1)), m);
                if (c != null) add(m, seen, c);
            }
        }
        m.calls.sort(Comparator.comparingInt(Call::line));
    }

    private static int lineOf(PyParser.Stmt st, String masked, int idx) {
        int n = 0;
        for (int i = 0; i < idx && i < masked.length(); i++) if (masked.charAt(i) == '\n') n++;
        return st.line() + n;
    }

    private static void add(PM m, Set<String> seen, Call c) {
        String key = c.targetClass() + "#" + c.method() + "@" + c.line();
        if (seen.add(key)) m.calls.add(c);
    }

    private static final Set<String> MANAGER_ATTRS = Set.of("objects", "_default_manager", "_base_manager", "all_objects",
            "active_objects", "published");

    private Call resolveCall(PF pf, Cx owner, Map<String, Cx> vars, String[] parts, int line, PM caller) {
        String head = parts[0];
        String name = parts[parts.length - 1];
        if (head.equals("self") || head.equals("cls")) {
            if (parts.length == 2) {
                PM t = findMethod(owner, name);
                if (t != null && t != caller) return callTo(t, line);
                if (t == null && isModel(owner) && Set.of("save", "delete", "refresh_from_db").contains(name)) return orm(owner, name, line);
                return null;
            }
            if (parts.length == 3) {
                FieldInfo f = findField(owner, parts[1]);
                Cx at = f == null || f.resolvedClass() == null ? null : classesById.get(f.resolvedClass());
                if (at == null) return null;
                PM t = findMethod(at, name);
                if (t != null) return callTo(t, line);
                if (isModel(at) && f.injected()) return orm(at, name, line);
            }
            if (parts.length >= 3 && MANAGER_ATTRS.contains(parts[1]) && isModel(owner)) return ormOrManager(owner, name, line);
            return null;
        }
        if (vars.containsKey(head)) {
            Cx t = vars.get(head);
            if (parts.length == 2) {
                PM tm = findMethod(t, name);
                if (tm != null) return callTo(tm, line);
                if (isModel(t) && Set.of("save", "delete", "refresh_from_db", "full_clean").contains(name)) return orm(t, name, line);
                if ("serializer".equals(t.attrs.get("djangoKind")) && name.equals("save")) {
                    PM create = findMethod(t, "create");
                    if (create == null) create = findMethod(t, "update");
                    if (create != null) return callTo(create, line);
                }
                return null;
            }
            if (parts.length >= 3 && MANAGER_ATTRS.contains(parts[1]) && isModel(t)) return ormOrManager(t, name, line);
            return null;
        }
        Cx cls = classByName(pf, head, false);
        if (cls != null) {
            if (parts.length >= 3 && MANAGER_ATTRS.contains(parts[1]) && isModel(cls)) return ormOrManager(cls, name, line);
            if (parts.length == 2) {
                PM t = findMethod(cls, name);
                if (t != null) return callTo(t, line);
                return null;
            }
            if (parts.length == 1) {
                if (Set.of("ENTITY", "DTO", "EXCEPTION", "MODEL").contains(cls.layer)) return null;
                PM init = findMethod(cls, "__init__");
                if (init != null) return callTo(init, line);
            }
            return null;
        }
        PF mod = parts.length >= 2 ? moduleOfAlias(pf, head) : null;
        if (mod != null) {
            if (parts.length == 2) {
                PM f = functionIn(mod, parts[1]);
                if (f != null) return callTo(f, line);
            } else if (parts.length >= 3) {
                Cx cc = classInModule(mod, parts[1], new HashSet<>());
                if (cc != null) {
                    if (parts.length == 3) {
                        PM t = findMethod(cc, name);
                        if (t != null) return callTo(t, line);
                    } else if (MANAGER_ATTRS.contains(parts[2]) && isModel(cc)) return ormOrManager(cc, name, line);
                }
            }
            return null;
        }
        if (parts.length == 1) {
            PM f = functionByExpr(pf, head);
            if (f != null && f != caller) return callTo(f, line);
        }
        return null;
    }

    private Call ormOrManager(Cx model, String op, int line) {
        for (Cx mgr : model.managers) {
            PM t = findMethod(mgr, op);
            if (t != null) return callTo(t, line);
        }
        return orm(model, op, line);
    }

    // ───────────────────────── dependencies ─────────────────────────

    private static final Pattern IDENT = Pattern.compile("[A-Za-z_]\\w*");

    private void computeDeps(Cx c) {
        for (Cx b : c.bases) dep(c, b, "EXTENDS");
        for (PM m : c.methods) {
            for (Call call : m.calls) {
                Cx t = classesById.get(call.targetClass());
                if (t == null || t == c) continue;
                dep(c, t, call.library() ? "USES" : "CALLS");
            }
        }
        for (FieldInfo f : c.fields) {
            if (f.resolvedClass() == null) continue;
            Cx t = classesById.get(f.resolvedClass());
            if (t != null && t != c) dep(c, t, f.injected() ? "INJECTS" : "USES");
        }
        // class names used in the code (imported or defined next to it)
        Set<String> idents = new HashSet<>();
        if (c.cls != null) {
            for (String d : c.cls.decorators) collect(idents, d);
            for (PyParser.Stmt s : c.cls.body) collect(idents, s.text());
        }
        for (PM m : c.methods) {
            if (m.fn == null) continue;
            for (String d : m.fn.decorators) collect(idents, d);
            for (PyParser.Param p : m.fn.params) if (p.annotation() != null) collect(idents, p.annotation());
            for (PyParser.Stmt s : m.fn.body) collect(idents, s.text());
        }
        for (String id : idents) {
            if (!Character.isUpperCase(id.charAt(0))) continue;
            Cx t = classByName(c.pf, id, false);
            if (t != null && t != c && !t.moduleClass) dep(c, t, "USES");
        }
        // string references such as ForeignKey("shop.Owner")
        for (FieldInfo f : c.fields) if (f.resolvedClass() != null && classesById.get(f.resolvedClass()) != null && classesById.get(f.resolvedClass()) != c) {
            dep(c, classesById.get(f.resolvedClass()), "USES");
        }
    }

    private static void collect(Set<String> out, String text) {
        Matcher m = IDENT.matcher(PyParser.maskStrings(text));
        while (m.find()) out.add(m.group());
    }

    private static void dep(Cx from, Cx to, String kind) {
        if (from == to || to.id.equals(from.id)) return;
        from.deps.computeIfAbsent(to.id, k -> new LinkedHashSet<>()).add(kind);
    }

    // ───────────────────────── materialise ─────────────────────────

    private ClassInfo toInfo(Cx c) {
        List<MethodInfo> ms = new ArrayList<>();
        for (PM m : c.methods) {
            List<Param> params = new ArrayList<>();
            StringBuilder sig = new StringBuilder(m.name).append('(');
            boolean first = true;
            List<String> mods = new ArrayList<>();
            if (m.fn != null) {
                if (m.fn.async) mods.add("async");
                for (PyParser.Param p : m.fn.params) {
                    if (p.name().equals("self") || p.name().equals("cls")) continue;
                    params.add(new Param(p.name(), p.annotation() == null ? "" : p.annotation()));
                    if (!first) sig.append(", ");
                    sig.append(p.name());
                    if (p.annotation() != null) sig.append(": ").append(p.annotation());
                    first = false;
                }
                for (String d : m.fn.decorators) {
                    String n = lastSeg(d.contains("(") ? d.substring(0, d.indexOf('(')) : d);
                    if (n.equals("staticmethod") || n.equals("classmethod") || n.equals("property")) mods.add(n);
                }
            }
            sig.append(')');
            if (m.fn != null && m.fn.returns != null) sig.append(" -> ").append(m.fn.returns);
            ms.add(new MethodInfo(m.id, m.name, sig.toString(), m.fn == null ? null : m.fn.returns, params, m.line, m.endLine, m.anns,
                    mods, m.doc, m.calls, m.name.equals("__init__"), m.endpointId, m.loc));
        }
        List<Dep> deps = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : c.deps.entrySet()) deps.add(new Dep(e.getKey(), primary(e.getValue())));
        String ext = c.baseTexts.isEmpty() ? null : c.baseTexts.get(0);
        List<String> impls = c.baseTexts.size() > 1 ? new ArrayList<>(c.baseTexts.subList(1, c.baseTexts.size())) : List.of();
        return new ClassInfo(c.id, c.name, c.pkg, c.pf.path, c.line, c.endLine, c.kind, c.layer, c.module, c.anns, ext, impls,
                c.fields, ms, c.doc, deps, c.test, c.attrs, c.imports, null);
    }

    private static String primary(Set<String> kinds) {
        for (String k : List.of("EXTENDS", "IMPLEMENTS", "INJECTS", "CALLS", "USES")) if (kinds.contains(k)) return k;
        return "USES";
    }
}
