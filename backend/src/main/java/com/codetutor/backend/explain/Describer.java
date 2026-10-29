package com.codetutor.backend.explain;

import com.codetutor.backend.analysis.Knowledge;
import com.codetutor.backend.analysis.model.AnalysisModel.*;
import com.codetutor.backend.analysis.util.TypeUtil;
import com.codetutor.backend.explain.ExplainModel.Explanation;
import com.codetutor.backend.explain.ExplainModel.Link;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Turns the analysed structure into short, beginner-friendly explanations. Everything here is derived from the
 * real code (annotations, call graph, naming, javadoc, schema) — no LLM is required for the popups.
 */
public class Describer {

    private static final Map<String, String> VERBS = new LinkedHashMap<>();
    static {
        for (String v : List.of("get", "find", "fetch", "load", "read", "retrieve", "query", "search", "list", "lookup",
                "select", "obtain")) VERBS.put(v, "Retrieves");
        for (String v : List.of("is", "has", "can", "should", "check", "exists", "contains", "verify", "matches",
                "supports", "equals")) VERBS.put(v, "Checks");
        for (String v : List.of("validate")) VERBS.put(v, "Validates");
        for (String v : List.of("set", "update", "edit", "modify", "change", "patch", "put", "replace")) VERBS.put(v, "Updates");
        for (String v : List.of("add", "create", "insert", "save", "store", "persist", "register", "new", "append", "put"))
            VERBS.put(v, "Creates / saves");
        for (String v : List.of("delete", "remove", "clear", "destroy", "drop", "purge")) VERBS.put(v, "Deletes");
        for (String v : List.of("process", "handle", "execute", "perform", "run", "do", "apply", "on")) VERBS.put(v, "Handles");
        for (String v : List.of("build", "make", "generate", "construct", "compose", "assemble")) VERBS.put(v, "Builds");
        for (String v : List.of("convert", "map", "to", "parse", "format", "transform", "serialize", "print", "write"))
            VERBS.put(v, "Converts");
        for (String v : List.of("show", "display", "render", "view", "init")) VERBS.put(v, "Prepares / shows");
        for (String v : List.of("initialize", "setup", "configure", "start", "boot", "enable")) VERBS.put(v, "Sets up");
        for (String v : List.of("login", "authenticate", "signin")) VERBS.put(v, "Authenticates");
        for (String v : List.of("logout", "signout")) VERBS.put(v, "Signs out");
        for (String v : List.of("send", "publish", "notify", "emit", "dispatch")) VERBS.put(v, "Sends");
        for (String v : List.of("populate", "fill", "prepare")) VERBS.put(v, "Populates");
        for (String v : List.of("count", "calculate", "compute", "sum")) VERBS.put(v, "Computes");
        for (String v : List.of("filter", "sort", "order", "paginate")) VERBS.put(v, "Filters / orders");
        for (String v : List.of("extract", "resolve", "detect")) VERBS.put(v, "Extracts");
        for (String v : List.of("trigger", "throw", "raise")) VERBS.put(v, "Triggers");
    }

    private static final Map<String, String> ANN_TEXT = Map.ofEntries(
            Map.entry("Transactional", "Runs inside a database transaction."),
            Map.entry("ModelAttribute", "Runs before request handling to prepare data for the view/model."),
            Map.entry("InitBinder", "Customises how request data is bound to Java objects."),
            Map.entry("Bean", "Creates a Spring bean that other components can inject."),
            Map.entry("Cacheable", "Its result is cached."),
            Map.entry("CacheEvict", "Clears cached entries."),
            Map.entry("Scheduled", "Runs automatically on a schedule."),
            Map.entry("Async", "Runs asynchronously on another thread."),
            Map.entry("PostConstruct", "Runs once after the bean is created."),
            Map.entry("EventListener", "Reacts to application events."),
            Map.entry("PreAuthorize", "Access is restricted by a security expression."),
            Map.entry("Secured", "Access is restricted to specific roles."),
            Map.entry("Valid", "Input is validated before use."),
            Map.entry("Query", "Uses a custom query."),
            Map.entry("Override", ""));

    private final Knowledge k;

    public Describer(Knowledge k) { this.k = k; }

    // ═════════════════════════ classes ═════════════════════════

    public static String stem(ClassInfo c) {
        String n = c.name().replaceAll("(ServiceImpl|Service|Controller|RestController|Repository|Repo|Dao|DAO|Impl|"
                + "Dto|DTO|Request|Response|Entity|Mapper|Handler|Config|Configuration)$", "");
        if (c.attrs() != null && c.attrs().get("framework") != null) {
            n = n.replaceAll("(ViewSet|View|Serializer|ModelForm|Form|Admin|QuerySet|Manager|Middleware|Command|Permission|Mixin)$", "");
            if (c.attrs().containsKey("moduleFile")) return c.name();
        }
        if (n.isBlank()) n = c.name();
        return TypeUtil.humanize(n);
    }

    public String classSummary(ClassInfo c) {
        if (c.doc() != null && !c.doc().isBlank() && !"TEST".equals(c.layer())) {
            return TypeUtil.firstSentence(c.doc());
        }
        String py = pythonSummary(c);
        if (py != null) return py;
        String stem = stem(c);
        List<String> injected = injectedNames(c);
        String uses = injected.isEmpty() ? "" : " It relies on " + joinNatural(injected) + ".";
        switch (c.layer()) {
            case "CONTROLLER" -> {
                List<EndpointInfo> eps = k.endpointsOf(c);
                String list = eps.stream().limit(3).map(e -> e.httpMethod() + " " + e.path()).collect(Collectors.joining(", "));
                return "Web controller for " + stem + ": receives HTTP requests"
                        + (eps.isEmpty() ? "" : " (" + eps.size() + " endpoint" + (eps.size() > 1 ? "s" : "") + ", e.g. "
                        + list + (eps.size() > 3 ? ", …" : "") + ")") + " and returns "
                        + (eps.stream().allMatch(EndpointInfo::viewResponse) && !eps.isEmpty() ? "pages." : "responses.")
                        + uses;
            }
            case "SERVICE" -> { return "Service layer for " + stem + ": contains the business rules between the controllers and the data layer." + uses; }
            case "REPOSITORY" -> {
                ClassInfo e = k.entityOfRepository(c);
                String table = k.tableOfRepository(c);
                String q = c.methods().stream().map(MethodInfo::name).filter(n -> !n.equals(c.name())).limit(4)
                        .collect(Collectors.joining(", "));
                return "Data-access repository" + (e != null ? " for " + e.name() + " records" : "")
                        + (table != null ? " (table `" + table + "`)" : "") + ": reads and writes the database."
                        + (q.isEmpty() ? "" : " Custom queries: " + q + ".");
            }
            case "ENTITY" -> {
                String table = k.entityTable.get(c.id());
                int n = (int) c.fields().stream().filter(f -> !f.isStatic()).count();
                return c.name() + " is a persistent data model" + (table != null ? " stored in table `" + table + "`" : "")
                        + " with " + n + " field" + (n == 1 ? "" : "s") + relationText(c) + ".";
            }
            case "CONFIG" -> {
                String beans = c.methods().stream().filter(m -> m.annotations().stream().anyMatch(a -> a.name().equals("Bean")))
                        .map(MethodInfo::name).collect(Collectors.joining(", "));
                return "Configuration class" + (beans.isEmpty() ? "" : " that registers the beans " + beans) + ".";
            }
            case "SECURITY" -> { return securityClassSummary(c); }
            case "APPLICATION" -> { return "Application entry point: its main method boots Spring Boot, which scans the packages and wires all components together."; }
            case "ADVICE" -> {
                String handled = c.methods().stream().flatMap(m -> m.annotations().stream())
                        .filter(a -> a.name().equals("ExceptionHandler")).map(a -> a.arg("value")).filter(Objects::nonNull)
                        .map(s -> s.replace(".class", "")).collect(Collectors.joining(", "));
                return "Global exception handler: turns errors" + (handled.isEmpty() ? "" : " (" + handled + ")")
                        + " into clean HTTP responses.";
            }
            case "DTO" -> { return "Data-transfer object carrying " + fieldNames(c, 5) + " between the client and the server."; }
            case "EXCEPTION" -> { return "Custom exception " + c.name() + " signalling a specific error situation."; }
            case "UTIL" -> { return "Utility class with helper methods (" + methodNames(c, 4) + ")."; }
            case "TEST" -> {
                long tests = c.methods().stream().filter(m -> m.annotations().stream().anyMatch(a -> a.name().contains("Test"))).count();
                return "Automated test class with " + tests + " test" + (tests == 1 ? "" : "s") + ".";
            }
            case "MODEL" -> {
                if ("ENUM".equals(c.kind())) return "Enumeration of fixed values: " + fieldNames(c, 6) + ".";
                if ("RECORD".equals(c.kind())) return "Immutable data record holding " + fieldNames(c, 5) + ".";
                return "Domain model class " + c.name() + " holding " + fieldNames(c, 5) + ".";
            }
            case "COMPONENT" -> { return "Spring-managed component " + c.name() + " (" + methodNames(c, 3) + ")." + uses; }
            default -> {
                String kind = switch (c.kind()) { case "INTERFACE" -> "Interface"; case "ENUM" -> "Enum"; case "RECORD" -> "Record"; default -> "Class"; };
                return kind + " " + c.name() + " with " + c.methods().size() + " method" + (c.methods().size() == 1 ? "" : "s")
                        + (c.methods().isEmpty() ? "" : " (" + methodNames(c, 3) + ")") + "." + uses;
            }
        }
    }

    /** Summaries for Python / Django classes (null when the class is not Python or the generic text already fits). */
    private String pythonSummary(ClassInfo c) {
        String fw = c.attrs().get("framework");
        if (fw == null) return null;
        String kind = c.attrs().getOrDefault("djangoKind", "");
        String file = c.file();
        String stem = file.substring(file.lastIndexOf('/') + 1).replaceAll("\\.py$", "");
        if ("true".equals(c.attrs().get("moduleFile"))) {
            String fns = methodNames(c, 4);
            int n = c.methods().size();
            return switch (c.layer()) {
                case "CONTROLLER" -> "Views module “" + c.name() + "”: " + n + " view function" + (n == 1 ? "" : "s") + " (" + fns
                        + ") that receive HTTP requests and return pages or JSON.";
                case "CONFIG" -> stem.equals("urls") ? "URL configuration: maps URL patterns to the views that handle them."
                        : stem.startsWith("settings") || file.contains("/settings/")
                        ? "Project settings: installed apps, middleware, database, authentication and security options."
                        : "Configuration module (" + stem + ").";
                case "APPLICATION" -> stem.equals("manage") ? "Django's command-line entry point: runs the development server, migrations and other management commands."
                        : "Server entry point (" + stem + ") that exposes the application to a web server (WSGI / ASGI).";
                case "SERVICE" -> "Service functions (" + fns + ") holding the business rules between the views and the data layer.";
                case "SECURITY" -> "Authentication / permission helpers (" + fns + ").";
                case "COMPONENT" -> "Signal handlers, tasks or middleware helpers (" + fns + ").";
                case "UTIL" -> "Helper functions (" + fns + ").";
                case "ENTITY" -> "Helper functions that live next to the models (" + fns + ").";
                default -> "Python module with " + n + " function" + (n == 1 ? "" : "s") + (n == 0 ? "" : " (" + fns + ")") + ".";
            };
        }
        String name = stem(c);
        switch (kind) {
            case "view" -> {
                List<EndpointInfo> eps = k.endpointsOf(c);
                String verbs = eps.stream().map(EndpointInfo::httpMethod).distinct().collect(Collectors.joining(" / "));
                return "Class-based view for " + name + (eps.isEmpty() ? "" : ": serves " + verbs + " requests on "
                        + eps.stream().map(EndpointInfo::path).distinct().limit(2).collect(Collectors.joining(", ")))
                        + (eps.stream().allMatch(EndpointInfo::viewResponse) && !eps.isEmpty() ? " and renders HTML pages." : ".");
            }
            case "viewset" -> {
                List<EndpointInfo> eps = k.endpointsOf(c);
                return "REST API ViewSet for " + name + ": exposes " + eps.size() + " endpoint" + (eps.size() == 1 ? "" : "s")
                        + " (list, create, retrieve, update, delete as available) backed by a serializer and the database.";
            }
            case "serializer" -> {
                String model = c.attrs().get("meta.model");
                return "Serializer for " + name + ": validates incoming data and converts " + (model == null ? "objects" : model.strip() + " records")
                        + " to and from JSON. Fields: " + fieldNames(c, 5) + ".";
            }
            case "form" -> { return "Form for " + name + ": validates what the user submitted (" + fieldNames(c, 5) + ")."; }
            case "admin" -> { return "Django admin configuration for " + name + ": how its records are listed and edited in the admin site."; }
            case "appconfig" -> { return "App configuration: registers the app with Django and can run start-up code."; }
            case "manager" -> {
                String e = c.attrs().get("entityType");
                return "Custom manager / queryset" + (e == null ? "" : " for " + e) + ": reusable database queries (" + methodNames(c, 4) + ").";
            }
            case "permission" -> { return "Permission class: decides whether a request may use a view (has_permission / has_object_permission)."; }
            case "authentication" -> { return "Authentication class / backend: identifies the user behind each request."; }
            case "middleware" -> { return "Middleware: runs around every request and response before and after the views."; }
            case "command" -> { return "Management command: run it with `python manage.py " + TypeUtil.snake(c.name().replaceAll("Command$", "")) + "`."; }
            case "schema" -> { return "Pydantic schema describing the data " + name + " (" + fieldNames(c, 5) + ")."; }
            default -> { return null; }
        }
    }

    private String securityClassSummary(ClassInfo c) {
        String n = c.name().toLowerCase(Locale.ROOT);
        if ("true".equals(c.attrs().get("filter"))) {
            return "Request filter that runs before the controllers: it reads the credentials/token from each request and "
                    + "authenticates the caller.";
        }
        if (c.implementsTypes().stream().anyMatch(t -> TypeUtil.raw(t).equals("UserDetailsService"))) {
            return "Loads user accounts from the database so Spring Security can verify logins.";
        }
        if (n.contains("jwt") || n.contains("token")) return "Creates, parses and validates JWT tokens used to identify logged-in users.";
        if (c.annotations().stream().anyMatch(a -> a.name().equals("EnableWebSecurity")) || n.contains("config")) {
            Security s = k.result.security();
            return "Security configuration: defines which URLs are public, how requests are authenticated"
                    + (s != null && !s.mechanisms().isEmpty() ? " (" + String.join(", ", s.mechanisms()) + ")" : "") + ".";
        }
        return "Security component supporting authentication and authorization.";
    }

    private String relationText(ClassInfo c) {
        List<String> rel = new ArrayList<>();
        for (FieldInfo f : c.fields()) {
            for (Ann a : f.annotations()) {
                switch (a.name()) {
                    case "OneToMany" -> rel.add("has many " + TypeUtil.humanize(innerType(f.type())));
                    case "ManyToMany" -> rel.add("is linked many-to-many with " + TypeUtil.humanize(innerType(f.type())));
                    case "ManyToOne", "ForeignKey" -> rel.add("belongs to a " + TypeUtil.humanize(f.type()));
                    case "OneToOne", "OneToOneField" -> rel.add("is linked one-to-one with " + TypeUtil.humanize(f.type()));
                    case "ManyToManyField" -> rel.add("is linked many-to-many with " + TypeUtil.humanize(f.type()));
                    default -> { }
                }
            }
        }
        return rel.isEmpty() ? "" : "; it " + joinNatural(rel);
    }

    private static String innerType(String t) {
        List<String> a = TypeUtil.typeArgs(t);
        return TypeUtil.raw(a.isEmpty() ? t : a.get(0));
    }

    private List<String> injectedNames(ClassInfo c) {
        List<String> out = new ArrayList<>();
        for (FieldInfo f : c.fields()) if (f.injected() && f.resolvedClass() != null) out.add(TypeUtil.raw(f.type()));
        return out;
    }

    private static String fieldNames(ClassInfo c, int max) {
        List<String> n = c.fields().stream().filter(f -> !f.isStatic() || "ENUM".equals(c.kind())).map(FieldInfo::name).toList();
        if (n.isEmpty()) return "no fields";
        return String.join(", ", n.subList(0, Math.min(max, n.size()))) + (n.size() > max ? ", …" : "");
    }

    private static String methodNames(ClassInfo c, int max) {
        List<String> n = c.methods().stream().filter(m -> !m.constructor()).map(MethodInfo::name).distinct().toList();
        if (n.isEmpty()) return "no methods";
        return String.join(", ", n.subList(0, Math.min(max, n.size()))) + (n.size() > max ? ", …" : "");
    }

    static String joinNatural(List<String> items) {
        if (items.isEmpty()) return "";
        if (items.size() == 1) return items.get(0);
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
    }

    // ═════════════════════════ methods ═════════════════════════

    public String methodSummary(ClassInfo c, MethodInfo m) {
        if (m.doc() != null && !m.doc().isBlank()) return TypeUtil.firstSentence(m.doc());
        List<EndpointInfo> eps = k.endpointsByHandler.getOrDefault(m.id(), List.of());
        StringBuilder sb = new StringBuilder();
        if (!eps.isEmpty()) {
            EndpointInfo e = eps.get(0);
            sb.append("Handles ").append(e.httpMethod()).append(' ').append(e.path()).append(": ");
            sb.append(lowerFirst(verbPhrase(c, m))).append('.');
        } else if (m.constructor()) {
            String injected = m.params().stream().map(p -> TypeUtil.raw(p.type())).collect(Collectors.joining(", "));
            boolean py = c.attrs().get("framework") != null;
            sb.append(py ? "Initialiser" : "Constructor").append(injected.isEmpty() ? " creating a new " + c.name() + "."
                    : py ? ": receives " + injected + " and keeps them for later use."
                    : ": receives " + injected + " from Spring (dependency injection) and stores them for later use.");
        } else {
            sb.append(verbPhrase(c, m)).append('.');
        }
        String data = dataAccess(m);
        if (data != null) sb.append(' ').append(data);
        return sb.toString();
    }

    private String verbPhrase(ClassInfo c, MethodInfo m) {
        List<String> w = TypeUtil.words(m.name());
        if (w.isEmpty()) return "Executes " + m.name();
        String first = w.get(0);
        String verb = VERBS.get(first);
        List<String> rest = w.subList(1, w.size());
        String entity = entityHint(c);
        if (verb == null) {
            // e.g. "processCreationForm" handled above; "owner" etc.
            return "Performs “" + String.join(" ", w) + "”" + (entity.isEmpty() ? "" : " for " + entity);
        }
        int by = rest.indexOf("by");
        String obj;
        String suffix = "";
        if (by >= 0) {
            obj = String.join(" ", rest.subList(0, by));
            suffix = " by " + String.join(" ", rest.subList(by + 1, rest.size()));
        } else {
            obj = String.join(" ", rest);
        }
        obj = obj.replaceAll("\\bstarting with\\b", "starting with");
        if (verb.equals("Authenticates") || verb.equals("Signs out")) {
            if (obj.isBlank() || obj.equals("auth")) return verb + " the user";
        }
        if (obj.isBlank()) obj = entity;
        if (obj.equals("all")) obj = "all " + entity + (entity.endsWith("s") ? "" : "s");
        if (m.name().startsWith("is") || m.name().startsWith("has") || m.name().startsWith("can")) {
            return "Checks whether " + (entity.isEmpty() ? "" : "the " + entity + " ") + String.join(" ", w.subList(1, w.size()))
                    .replaceAll("^$", "the condition holds");
        }
        if (first.equals("init") && !rest.isEmpty()) return "Prepares the " + obj + suffix + " (initial state shown to the user)";
        if (first.equals("process") || first.equals("handle")) {
            return "Processes the " + (obj.isBlank() ? "submitted request" : obj) + suffix
                    + " the user submitted: checks the input, applies the change and sends the user on";
        }
        return verb + " " + (obj.isBlank() ? "data" : "the " + obj) + suffix;
    }

    private String entityHint(ClassInfo c) {
        if ("REPOSITORY".equals(c.layer())) {
            String e = c.attrs().get("entityType");
            if (e != null) return TypeUtil.humanize(e);
        }
        if (c.attrs() != null && c.attrs().containsKey("moduleFile")) return "";
        String s = stem(c);
        return s.equals(TypeUtil.humanize(c.name())) && !"CONTROLLER".equals(c.layer()) ? "" : s;
    }

    /** "Reads table X" / "Writes table X" when the method (directly) calls a repository. */
    private String dataAccess(MethodInfo m) {
        List<String> reads = new ArrayList<>();
        List<String> writes = new ArrayList<>();
        for (Call call : m.calls()) {
            ClassInfo target = k.classById.get(call.targetClass());
            if (target == null) continue;
            boolean ormModel = "ENTITY".equals(target.layer()) && k.entityTable.containsKey(target.id());
            if (!ormModel && !"REPOSITORY".equals(target.layer())) continue;
            String table = ormModel ? k.entityTable.get(target.id()) : k.tableOfRepository(target);
            String label = table != null ? "table `" + table + "`" : target.name();
            if (call.method().matches("^(save|delete|insert|update|remove|persist|merge|flush|create|bulk_|get_or_create|update_or_create).*")) {
                if (!writes.contains(label)) writes.add(label);
            } else if (!reads.contains(label)) reads.add(label);
        }
        List<String> parts = new ArrayList<>();
        if (!reads.isEmpty()) parts.add("reads " + joinNatural(reads));
        if (!writes.isEmpty()) parts.add("writes to " + joinNatural(writes));
        return parts.isEmpty() ? null : "It " + String.join(" and ", parts) + ".";
    }

    private static String lowerFirst(String s) { return s == null || s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1); }

    // ═════════════════════════ explanations (popup payloads) ═════════════════════════

    public Explanation classExplanation(ClassInfo c) {
        List<String> facts = new ArrayList<>();
        facts.add(layerLabel(c.layer()) + " · module “" + c.module() + "”");
        String ann = c.annotations().stream().map(Ann::render).limit(4).collect(Collectors.joining(" "));
        if (!ann.isBlank()) facts.add("Annotations: " + ann);
        if (c.extendsType() != null) facts.add("Extends " + TypeUtil.raw(c.extendsType()));
        if (!c.implementsTypes().isEmpty() && !"INTERFACE".equals(c.kind())) {
            facts.add("Implements " + c.implementsTypes().stream().map(TypeUtil::raw).collect(Collectors.joining(", ")));
        }
        facts.add(c.methods().stream().filter(m -> !m.constructor()).count() + " methods · " + c.fields().size() + " fields · "
                + Math.max(1, c.endLine() - c.line() + 1) + " lines");
        List<EndpointInfo> eps = k.endpointsOf(c);
        if (!eps.isEmpty()) {
            facts.add("Endpoints: " + eps.stream().limit(5).map(e -> e.httpMethod() + " " + e.path()).collect(Collectors.joining(", "))
                    + (eps.size() > 5 ? " (+" + (eps.size() - 5) + " more)" : ""));
        }
        String table = k.entityTable.get(c.id());
        if (table != null) facts.add("Stored in table `" + table + "`");
        String repoTable = k.tableOfRepository(c);
        if (repoTable != null) facts.add("Manages table `" + repoTable + "`");

        List<Link> links = new ArrayList<>();
        int n = 0;
        for (Dep d : c.dependsOn()) {
            ClassInfo t = k.classById.get(d.target());
            if (t == null || t.test() || n >= 8) continue;
            links.add(new Link(t.name(), "class", t.id(), t.file(), t.line(), "Uses"));
            n++;
        }
        n = 0;
        for (ClassInfo u : k.dependentsOf(c.id())) {
            if (n++ >= 8) break;
            links.add(new Link(u.name(), "class", u.id(), u.file(), u.line(), "Used by"));
        }
        return new Explanation(kindLabel(c), c.id(), c.name(), c.pkg(), classSummary(c), facts, links, c.file(), c.line(),
                c.endLine(), tagsOf(c), c.layer());
    }

    public Explanation methodExplanation(ClassInfo c, MethodInfo m) {
        List<String> facts = new ArrayList<>();
        facts.add("In " + c.name() + " (" + layerLabel(c.layer()).toLowerCase(Locale.ROOT) + ")");
        if (!m.constructor()) {
            facts.add("Takes " + (m.params().isEmpty() ? "no input" : m.params().stream()
                    .map(p -> p.type() + " " + p.name()).collect(Collectors.joining(", "))));
            if (m.returnType() != null && !m.returnType().equals("void")) facts.add("Returns " + m.returnType());
        }
        for (Ann a : m.annotations()) {
            String t = ANN_TEXT.get(a.name());
            if (t != null && !t.isEmpty()) facts.add(t);
        }
        List<EndpointInfo> eps = k.endpointsByHandler.getOrDefault(m.id(), List.of());
        for (EndpointInfo e : eps) {
            facts.add("Endpoint " + e.httpMethod() + " " + e.fullPath() + " — access: " + accessLabel(e));
            if (!e.params().isEmpty()) {
                facts.add("Input: " + e.params().stream().map(p -> p.name() + " (" + p.source() + ")").collect(Collectors.joining(", ")));
            }
        }
        facts.add(m.loc() + " lines (" + m.line() + "–" + m.endLine() + ")");

        List<Link> links = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Call call : m.calls()) {
            String key = call.targetClassName() + "." + call.method();
            if (!seen.add(key) || seen.size() > 8) continue;
            ClassInfo tc = k.classById.get(call.targetClass());
            MethodInfo tm = call.targetMethodId() == null ? null : k.methodById.get(call.targetMethodId());
            links.add(new Link(key + (call.library() ? " (Spring Data)" : ""), tm != null ? "method" : "class",
                    tm != null ? tm.id() : call.targetClass(), tc != null ? tc.file() : null,
                    tm != null ? tm.line() : tc != null ? tc.line() : 0, "Calls"));
        }
        int n = 0;
        for (MethodInfo caller : k.callers.getOrDefault(m.id(), List.of())) {
            ClassInfo cc = k.classOf(caller);
            if (cc == null || n++ >= 6) continue;
            links.add(new Link(cc.name() + "." + caller.name(), "method", caller.id(), cc.file(), caller.line(), "Called by"));
        }
        for (EndpointInfo e : eps) {
            links.add(new Link("See data flow of " + e.httpMethod() + " " + e.path(), "flow", e.id(), c.file(), m.line(), "Flow"));
        }
        List<String> tags = new ArrayList<>();
        tags.add(m.constructor() ? "constructor" : "method");
        if (!eps.isEmpty()) tags.add("endpoint");
        return new Explanation(m.constructor() ? "constructor" : "method", m.id(), c.name() + "." + m.name() + "()",
                m.signature(), methodSummary(c, m), facts, links, c.file(), m.line(), m.endLine(), tags, c.layer());
    }

    public Explanation endpointExplanation(EndpointInfo e) {
        ClassInfo c = k.classById.get(e.controller());
        MethodInfo m = k.methodById.get(e.handler());
        List<String> facts = new ArrayList<>();
        facts.add("Access: " + accessLabel(e));
        if (!e.params().isEmpty()) facts.add("Input: " + e.params().stream().map(p -> p.name() + " (" + p.source() + ")").collect(Collectors.joining(", ")));
        facts.add(e.viewResponse() ? "Returns an HTML page (view)" : "Returns " + (e.returns() == null ? "data" : e.returns()));
        List<Link> links = new ArrayList<>();
        links.add(new Link("Data flow", "flow", e.id(), e.file(), e.line(), "Flow"));
        if (c != null && m != null) links.add(new Link(c.name() + "." + m.name(), "method", m.id(), c.file(), m.line(), "Handler"));
        return new Explanation("endpoint", e.id(), e.httpMethod() + " " + e.fullPath(), c == null ? null : c.name(),
                m != null && c != null ? methodSummary(c, m) : "REST endpoint.", facts, links, e.file(), e.line(), e.line(),
                List.of("endpoint"), "CONTROLLER");
    }

    public Explanation tableExplanation(Table t) {
        List<String> facts = new ArrayList<>();
        for (Column c : t.columns()) {
            facts.add(c.name() + " " + c.type() + (c.primaryKey() ? " · PK" : "") + (c.foreignKey() != null ? " · FK→" + c.foreignKey() : "")
                    + (!c.nullable() ? " · required" : ""));
        }
        List<Link> links = new ArrayList<>();
        if (t.entity() != null) {
            ClassInfo e = k.classById.get(t.entity());
            if (e != null) links.add(new Link(e.name(), "class", e.id(), e.file(), e.line(), "Entity"));
        }
        return new Explanation("table", t.name(), t.name(), t.source(), t.description(), facts, links, t.file(), t.line(), t.line(),
                List.of("table"), "ENTITY");
    }

    public String accessLabel(EndpointInfo e) {
        String a = e.access();
        if (a == null) return "public";
        if (a.equals("PUBLIC")) return "public (no login needed)";
        if (a.equals("AUTHENTICATED")) return "login required";
        if (a.startsWith("ROLE:")) return "requires role " + a.substring(5);
        if (a.equals("DENIED")) return "blocked for everyone";
        return a.toLowerCase(Locale.ROOT);
    }

    static String layerLabel(String l) {
        return switch (l) {
            case "CONTROLLER" -> "Controller";
            case "SERVICE" -> "Service";
            case "REPOSITORY" -> "Repository";
            case "ENTITY" -> "Entity";
            case "DTO" -> "DTO";
            case "CONFIG" -> "Configuration";
            case "SECURITY" -> "Security";
            case "COMPONENT" -> "Component";
            case "APPLICATION" -> "Application";
            case "ADVICE" -> "Exception handler";
            case "EXCEPTION" -> "Exception";
            case "UTIL" -> "Utility";
            case "MODEL" -> "Model";
            case "TEST" -> "Test";
            default -> "Class";
        };
    }

    private static String kindLabel(ClassInfo c) {
        return switch (c.kind()) { case "INTERFACE" -> "interface"; case "ENUM" -> "enum"; case "RECORD" -> "record"; default -> "class"; };
    }

    private static List<String> tagsOf(ClassInfo c) {
        List<String> t = new ArrayList<>();
        t.add(kindLabel(c));
        t.add(c.layer().toLowerCase(Locale.ROOT));
        return t;
    }

    // ═════════════════════════ files ═════════════════════════

    public Explanation fileExplanation(String path) {
        FileInfo f = k.fileByPath.get(path);
        List<Link> links = new ArrayList<>();
        List<String> facts = new ArrayList<>();
        String title = path.substring(path.lastIndexOf('/') + 1);
        String layer = null;
        if (f != null) {
            facts.add(f.language() + " · " + f.lines() + " lines · " + humanSize(f.size()));
            layer = f.layer();
            if (f.module() != null && !f.module().isBlank()) facts.add("Module “" + f.module() + "”");
        }
        for (ClassInfo c : k.result.classes()) {
            if (c.file().equals(path) && !c.nested()) links.add(new Link(c.name(), "class", c.id(), c.file(), c.line(), "Defines"));
        }
        JsFile js = k.jsByPath.get(path);
        if (js != null) {
            for (String imp : js.imports()) {
                links.add(new Link(imp.substring(imp.lastIndexOf('/') + 1), "file", imp, imp, 1, "Imports"));
            }
            for (ApiCall a : js.apiCalls()) {
                facts.add("Calls " + a.method() + " " + a.path() + (a.endpointId() != null ? "  ✓ backend endpoint" : ""));
                if (a.endpointId() != null) links.add(new Link(a.method() + " " + a.path(), "flow", a.endpointId(), path, a.line(), "Calls API"));
            }
        }
        String summary = f != null && f.summary() != null ? f.summary() : "Project file " + title + ".";
        return new Explanation("file", path, title, path, summary, facts, links, path, 1, f == null ? 1 : f.lines(),
                List.of(f == null ? "file" : f.language()), layer);
    }

    private static String humanSize(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", b / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", b / 1024.0 / 1024.0);
    }

    // ═════════════════════════ file summaries at analysis time ═════════════════════════

    /** One-or-two sentence description of any project file. */
    public String fileSummary(Path root, FileInfo f, List<Library> libs) {
        String path = f.path();
        String name = path.substring(path.lastIndexOf('/') + 1);
        String lower = name.toLowerCase(Locale.ROOT);
        JsFile js = k.jsByPath.get(path);
        if (js != null) return js.summary();
        List<ClassInfo> defined = k.result.classes().stream().filter(c -> c.file().equals(path) && !c.nested()).toList();
        if (!defined.isEmpty()) {
            ClassInfo main = defined.get(0);
            return main.summary() != null ? main.summary() : classSummary(main);
        }
        try {
            switch (lower) {
                case "pom.xml": {
                    long deps = libs.stream().filter(l -> "maven".equals(l.source())).count();
                    String java = firstMatch(Files.readString(root.resolve(path)), "<java.version>([^<]+)</java.version>");
                    return "Maven build file: defines the project, its " + deps + " dependencies"
                            + (java != null ? " and Java " + java : "") + ", and how it is built and packaged.";
                }
                case "package.json": {
                    long deps = libs.stream().filter(l -> "npm".equals(l.source())).count();
                    return "npm manifest: lists " + deps + " JavaScript packages and the scripts used to run/build the front-end.";
                }
                case "readme.md": return "Project documentation. " + readmeIntro(root.resolve(path));
                case "dockerfile": return "Docker recipe describing how to package and run the application in a container.";
                case "docker-compose.yml": case "docker-compose.yaml": return "Docker Compose file starting the application together with its services.";
                case "vite.config.js": case "vite.config.ts": return "Vite configuration for the front-end dev server and build.";
                case "index.html": return "HTML entry page that loads the front-end application.";
                case ".gitignore": return "Lists files Git should not track.";
                case "mvnw": case "mvnw.cmd": return "Maven wrapper script so the project builds without a global Maven install.";
                default: break;
            }
            if (lower.startsWith("application") && (lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".properties"))) {
                return "Spring Boot configuration: " + configKeys(root.resolve(path)) + ".";
            }
            if (lower.endsWith(".sql")) {
                int tables = 0;
                if (k.result.schema() != null) {
                    tables = (int) k.result.schema().tables().stream().filter(t -> path.equals(t.file())).count();
                }
                String text = Files.readString(root.resolve(path));
                long creates = Pattern_count(text, "(?i)create\\s+table");
                long inserts = Pattern_count(text, "(?i)insert\\s+into");
                if (creates > 0) return "SQL script that creates " + creates + " table" + (creates > 1 ? "s" : "") + (tables > 0 ? "" : "") + ".";
                if (inserts > 0) return "SQL script that inserts " + inserts + " rows of sample/seed data.";
                return "SQL script.";
            }
            if (lower.endsWith(".html") && path.contains("templates")) {
                return "Server-side HTML template (view) rendered by a controller for the user interface.";
            }
            if (lower.endsWith(".css") || lower.endsWith(".scss")) return "Stylesheet defining the look of the user interface.";
            if (lower.endsWith(".md")) return "Documentation. " + readmeIntro(root.resolve(path));
            if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
                if (path.startsWith(".github/workflows")) return "GitHub Actions workflow: automated build/test pipeline.";
                return "YAML configuration file.";
            }
            if (lower.endsWith(".properties")) return "Properties file with key/value settings or messages.";
            if (lower.endsWith(".xml")) return "XML configuration/data file.";
            if (lower.endsWith(".json")) return "JSON data/configuration file.";
        } catch (IOException ignored) {
            // fall through to generic description
        }
        return f.language() + " file " + name + ".";
    }

    private static long Pattern_count(String text, String regex) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(text);
        long n = 0;
        while (m.find()) n++;
        return n;
    }

    private static String firstMatch(String text, String regex) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String readmeIntro(Path p) {
        try {
            for (String line : Files.readAllLines(p)) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#") || t.startsWith("[") || t.startsWith("!") || t.startsWith("<")) continue;
                return t.length() > 180 ? t.substring(0, 177) + "..." : t;
            }
        } catch (IOException ignored) {
            // no intro available
        }
        return "";
    }

    private static String configKeys(Path p) {
        try {
            Set<String> keys = new LinkedHashSet<>();
            for (String line : Files.readAllLines(p)) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                if (!line.startsWith(" ") && !line.startsWith("\t")) {
                    String key = t.contains(":") ? t.substring(0, t.indexOf(':')) : t.contains("=") ? t.substring(0, t.indexOf('=')) : t;
                    int dot = key.indexOf('.');
                    keys.add((dot > 0 ? key.substring(0, dot) : key).trim());
                }
            }
            keys.removeIf(String::isBlank);
            return keys.isEmpty() ? "application settings" : "settings for " + String.join(", ", keys.stream().limit(6).toList());
        } catch (IOException e) {
            return "application settings";
        }
    }
}
