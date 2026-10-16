package com.codetutor.backend.chat;

import com.codetutor.backend.analysis.Knowledge;
import com.codetutor.backend.analysis.model.AnalysisModel.*;
import com.codetutor.backend.analysis.util.TypeUtil;
import com.codetutor.backend.explain.Describer;
import com.codetutor.backend.explain.ExplainModel.Explanation;
import com.codetutor.backend.explain.ExplainModel.Link;
import com.codetutor.backend.search.VectorIndex;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Answers a question about a project using its two "brains": the knowledge graph (classes, calls, endpoints,
 * schema, security) and the vector index (semantic chunks). The deterministic answer is always grounded in real code;
 * if an LLM is configured it is used to phrase the final answer from that evidence.
 */
public class ChatEngine {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Source(String file, int startLine, int endLine, String label, String kind) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Action(String label, String tab, String id) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Selection(String file, int startLine, int endLine, String text) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Answer(String answer, List<Source> sources, Flow flow, List<Action> actions, String intent, boolean llm) {}

    private record Draft(String text, List<Source> sources, Flow flow, List<Action> actions, String intent) {}

    private static final Pattern FLOW_WORDS = Pattern.compile(
            "\\b(flow|how does|how do|how is|what happens|walk me|trace|lifecycle|sequence|journey|step by step|works?)\\b");
    private static final Pattern SECURITY_WORDS = Pattern.compile(
            "\\b(security|secure|auth|authenticat\\w*|authoriz\\w*|login|log in|logout|sign ?in|sign ?up|register|jwt|token|password|" +
                    "permission|role|session|cors|csrf|encrypt\\w*|bcrypt|filter chain)\\b");
    private static final Pattern SCHEMA_WORDS = Pattern.compile(
            "\\b(database|db|tables?|schema|columns?|erd|er diagram|relationship|foreign key|primary key|sql|cardinality|entit(y|ies)|orm)\\b");
    private static final Pattern ENDPOINT_WORDS = Pattern.compile("\\b(endpoints?|apis?|routes?|rest|urls?|controllers?|requests? mappings?)\\b");
    private static final Pattern LIST_WORDS = Pattern.compile("\\b(list|all|which|what|show|available|exposed|exist)\\b");
    private static final Pattern OVERVIEW_WORDS = Pattern.compile(
            "\\b(architecture|overview|structure|layers?|summar\\w*|tech stack|technolog\\w*|what is this|about this project|modules?|big picture|explain the project|explain this project)\\b");
    private static final Pattern DEPENDENCY_WORDS = Pattern.compile("\\b(depend\\w*|uses|used by|who calls|calls|coupled|relationships?)\\b");
    private static final Pattern EXPLAIN_WORDS = Pattern.compile("\\b(explain|what does|what is|purpose|role|describe|meaning|tell me about|responsible)\\b");
    private static final Pattern FIND_WORDS = Pattern.compile("\\b(where|find|locate|which file|implemented|defined|located)\\b");

    private final Knowledge k;
    private final LlmClient llm;
    private final Describer d;

    public ChatEngine(Knowledge k, LlmClient llm) {
        this.k = k;
        this.llm = llm;
        this.d = k.describer();
    }

    // ═════════════════════════ entry ═════════════════════════

    public Answer answer(String question, Selection selection, List<LlmClient.Turn> history) {
        String q = question == null ? "" : question.trim();
        String ql = q.toLowerCase(Locale.ROOT);
        Draft draft;
        if (selection != null && selection.file() != null && selection.startLine() > 0) draft = fromSelection(q, selection);
        else draft = route(q, ql);

        String text = draft.text();
        boolean usedLlm = false;
        if (llm.enabled()) {
            Optional<String> out = llm.complete(systemPrompt(), turns(history, q, draft));
            if (out.isPresent()) {
                text = out.get();
                usedLlm = true;
            }
        }
        return new Answer(text, dedupe(draft.sources()), draft.flow(), draft.actions(), draft.intent(), usedLlm);
    }

    private Draft route(String q, String ql) {
        List<EndpointInfo> eps = k.flows().matchEndpoints(q, 3);
        boolean flowish = FLOW_WORDS.matcher(ql).find();
        boolean sec = SECURITY_WORDS.matcher(ql).find();
        List<ClassInfo> classes = linkClasses(ql);
        List<MethodHit> methods = linkMethods(ql);

        // explicit "GET /path"
        if (Pattern.compile("\\b(get|post|put|delete|patch)\\s+/").matcher(ql).find() && !eps.isEmpty()) return endpointFlow(eps);
        if (sec && (flowish || ql.contains("login") || ql.contains("secur") || ql.contains("auth") || ql.contains("jwt")
                || ql.contains("token") || ql.contains("password") || ql.contains("role") || ql.contains("register"))) {
            return security(ql);
        }
        if (SCHEMA_WORDS.matcher(ql).find() && !(flowish && !eps.isEmpty())) return schema(ql);
        if (!eps.isEmpty() && (flowish || ql.contains("request") || ql.contains("endpoint") || ql.contains("what happens")
                || ql.contains("save") || ql.contains("create") || ql.contains("submit") || eps.size() == 1 && ENDPOINT_WORDS.matcher(ql).find())) {
            return endpointFlow(eps);
        }
        if (ENDPOINT_WORDS.matcher(ql).find() && (LIST_WORDS.matcher(ql).find() || classes.isEmpty())) return endpointList(ql, classes);
        if (OVERVIEW_WORDS.matcher(ql).find() && classes.isEmpty() && methods.isEmpty()) return overview();
        if (DEPENDENCY_WORDS.matcher(ql).find() && !classes.isEmpty()) return dependencies(classes.get(0));
        if (!methods.isEmpty() && (EXPLAIN_WORDS.matcher(ql).find() || flowish || FIND_WORDS.matcher(ql).find() || classes.isEmpty())) {
            return explainMethod(methods.get(0));
        }
        if (!classes.isEmpty()) return explainClass(classes.get(0), ql);
        if (flowish && !eps.isEmpty()) return endpointFlow(eps);
        if (OVERVIEW_WORDS.matcher(ql).find()) return overview();
        return search(q);
    }

    // ═════════════════════════ intents ═════════════════════════

    private Draft endpointFlow(List<EndpointInfo> eps) {
        EndpointInfo e = eps.get(0);
        Flow f = k.flows().endpointFlow(e.id());
        StringBuilder sb = new StringBuilder();
        sb.append("### `").append(e.httpMethod()).append(' ').append(e.fullPath()).append("` — how the data flows\n\n");
        sb.append(f.narrative()).append("\n\n");
        sb.append("**Access:** ").append(d.accessLabel(e)).append("\n");
        for (String n : f.notes()) sb.append("\n- ").append(n);
        if (eps.size() > 1) {
            sb.append("\n\nRelated endpoints you may mean: ").append(eps.stream().skip(1).map(x -> "`" + x.httpMethod() + " " + x.path() + "`")
                    .collect(Collectors.joining(", "))).append('.');
        }
        List<Source> src = new ArrayList<>();
        for (FlowStep s : f.steps()) {
            if (s.file() != null && s.line() > 0 && !"TABLE".equals(s.kind()) && !"FILTER".equals(s.kind())) {
                src.add(new Source(s.file(), s.line(), Math.max(s.line(), s.endLine()), s.title(), s.kind().toLowerCase(Locale.ROOT)));
            }
        }
        return new Draft(sb.toString().trim(), src, f, List.of(new Action("Open interactive flow", "flow", e.id())), "FLOW");
    }

    private Draft security(String ql) {
        Security s = k.result.security();
        if (s == null || !s.enabled()) {
            return new Draft("This project has **no Spring Security configuration**, so there is no login or permission check — every endpoint is open to everyone.\n\n"
                    + "If you expected authentication, look for it outside this repository (an API gateway or proxy).", List.of(),
                    k.flows().securityFlow(), List.of(new Action("Open security flow", "flow", "security")), "SECURITY");
        }
        boolean login = ql.contains("login") || ql.contains("log in") || ql.contains("sign") || ql.contains("register")
                || ql.contains("token") || ql.contains("jwt");
        StringBuilder sb = new StringBuilder();
        sb.append("### How security is implemented\n\n").append(s.summary()).append("\n\n");
        sb.append("**Request pipeline** (every request, in order):\n");
        for (SecurityFilter f : s.filters()) sb.append(f.order()).append(". `").append(f.name()).append("` — ").append(f.description()).append('\n');
        if (!s.rules().isEmpty()) {
            sb.append("\n**Authorization rules** (first match wins):\n");
            for (AuthRule r : s.rules().stream().limit(10).toList()) {
                sb.append("- `").append(r.httpMethod() == null ? "" : r.httpMethod() + " ").append(r.pattern()).append("` → ")
                        .append(accessText(r.access())).append('\n');
            }
        }
        if (s.passwordEncoder() != null) sb.append("\n**Passwords:** hashed with ").append(s.passwordEncoder()).append(".");
        if (s.sessionPolicy() != null) sb.append("\n**Sessions:** ").append(s.sessionPolicy().toLowerCase(Locale.ROOT)).append(".");
        for (String n : s.notes()) sb.append("\n- ").append(n);

        Flow flow = k.flows().securityFlow();
        List<Source> src = new ArrayList<>();
        String loginId = s.loginEndpoint();
        if (login && loginId != null && k.endpointById.containsKey(loginId)) {
            Flow lf = k.flows().endpointFlow(loginId);
            sb.append("\n\n### Login step by step (`").append(lf.title()).append("`)\n\n").append(lf.narrative());
            flow = lf;
            for (FlowStep st : lf.steps()) {
                if (st.file() != null && st.line() > 0 && !"FILTER".equals(st.kind()) && !"TABLE".equals(st.kind())) {
                    src.add(new Source(st.file(), st.line(), Math.max(st.line(), st.endLine()), st.title(), st.kind().toLowerCase(Locale.ROOT)));
                }
            }
        }
        if (s.configClass() != null && k.classById.containsKey(s.configClass())) {
            ClassInfo c = k.classById.get(s.configClass());
            src.add(0, new Source(c.file(), c.line(), c.endLine(), c.name(), "security"));
        }
        for (SecurityFilter f : s.filters()) {
            if (f.classId() != null && k.classById.containsKey(f.classId())) {
                ClassInfo c = k.classById.get(f.classId());
                src.add(new Source(c.file(), c.line(), c.endLine(), c.name(), "security"));
            }
        }
        if (s.tokenService() != null && k.classById.containsKey(s.tokenService())) {
            ClassInfo c = k.classById.get(s.tokenService());
            src.add(new Source(c.file(), c.line(), c.endLine(), c.name(), "security"));
        }
        return new Draft(sb.toString().trim(), src, flow, List.of(new Action("Open security flow", "flow", "security")), "SECURITY");
    }

    private static String accessText(String a) {
        if (a.equals("PUBLIC")) return "public";
        if (a.equals("AUTHENTICATED")) return "login required";
        if (a.startsWith("ROLE:")) return "role " + a.substring(5);
        return a.toLowerCase(Locale.ROOT);
    }

    private Draft schema(String ql) {
        Schema s = k.result.schema();
        if (s == null || s.tables().isEmpty()) {
            return new Draft("No database tables were detected (no JPA entities or `CREATE TABLE` scripts found).", List.of(), null, List.of(), "SCHEMA");
        }
        Set<String> qt = new HashSet<>(VectorIndex.tokens(ql));
        List<Table> named = s.tables().stream().filter(t -> {
            Set<String> tt = new HashSet<>(VectorIndex.tokens(t.name()));
            return !tt.isEmpty() && qt.containsAll(tt);
        }).toList();
        StringBuilder sb = new StringBuilder();
        List<Source> src = new ArrayList<>();
        if (!named.isEmpty() && named.size() <= 3) {
            for (Table t : named) {
                sb.append("### Table `").append(t.name()).append("`\n\n").append(t.description()).append("\n\n");
                sb.append("| Column | Type | Constraints |\n|---|---|---|\n");
                for (Column c : t.columns()) {
                    List<String> cs = new ArrayList<>();
                    if (c.primaryKey()) cs.add("PRIMARY KEY");
                    if (c.generated()) cs.add("auto-generated");
                    if (!c.nullable()) cs.add("NOT NULL");
                    if (c.unique()) cs.add("UNIQUE");
                    if (c.foreignKey() != null) cs.add("FK → " + c.foreignKey());
                    sb.append("| ").append(c.name()).append(" | ").append(c.type()).append(" | ").append(String.join(", ", cs)).append(" |\n");
                }
                for (Relation r : s.relations()) {
                    if (r.fromTable().equals(t.name())) sb.append("\n- Each row references **").append(r.toTable()).append("** (").append(r.fromColumn()).append(" → ").append(r.toColumn()).append(")");
                    if (r.toTable().equals(t.name())) sb.append("\n- Referenced by **").append(r.fromTable()).append("** (").append(r.cardinality().equals("N:1") ? "many " + r.fromTable() + " → one " + t.name() : r.cardinality()).append(")");
                }
                sb.append("\n\n");
                if (t.entity() != null && k.classById.containsKey(t.entity())) {
                    ClassInfo c = k.classById.get(t.entity());
                    src.add(new Source(c.file(), c.line(), c.endLine(), c.name(), "entity"));
                } else if (t.file() != null) src.add(new Source(t.file(), Math.max(1, t.line()), Math.max(1, t.line()), t.name(), "sql"));
            }
        } else {
            sb.append("### Database schema — ").append(s.tables().size()).append(" tables\n\n");
            for (Table t : s.tables()) {
                String pk = t.columns().stream().filter(Column::primaryKey).map(Column::name).collect(Collectors.joining(", "));
                sb.append("- **").append(t.name()).append("** — ").append(t.columns().size()).append(" columns").append(pk.isEmpty() ? "" : ", PK " + pk).append('\n');
                if (t.entity() != null && k.classById.containsKey(t.entity())) {
                    ClassInfo c = k.classById.get(t.entity());
                    src.add(new Source(c.file(), c.line(), c.endLine(), c.name(), "entity"));
                }
            }
            if (!s.relations().isEmpty()) {
                sb.append("\n**Relationships**\n");
                for (Relation r : s.relations()) {
                    sb.append("- `").append(r.toTable()).append("` 1 ─ N `").append(r.fromTable()).append("` (via `").append(r.fromColumn()).append("`)\n");
                }
            }
        }
        return new Draft(sb.toString().trim(), src, null, List.of(new Action("Open ER diagram", "database", named.isEmpty() ? null : named.get(0).name())), "SCHEMA");
    }

    private Draft endpointList(String ql, List<ClassInfo> classes) {
        List<EndpointInfo> eps = k.result.endpoints();
        if (eps.isEmpty()) return new Draft("No REST/web endpoints were detected in this project.", List.of(), null, List.of(), "ENDPOINTS");
        if (!classes.isEmpty()) {
            Set<String> ids = classes.stream().map(ClassInfo::id).collect(Collectors.toSet());
            List<EndpointInfo> f = eps.stream().filter(e -> ids.contains(e.controller())).toList();
            if (!f.isEmpty()) eps = f;
        }
        StringBuilder sb = new StringBuilder("### " + eps.size() + " endpoint" + (eps.size() == 1 ? "" : "s") + "\n\n| Method | Path | Handler | Access |\n|---|---|---|---|\n");
        List<Source> src = new ArrayList<>();
        for (EndpointInfo e : eps.stream().limit(40).toList()) {
            ClassInfo c = k.classById.get(e.controller());
            MethodInfo m = k.methodById.get(e.handler());
            sb.append("| ").append(e.httpMethod()).append(" | `").append(e.path()).append("` | ")
                    .append(c == null ? "" : c.name()).append('.').append(m == null ? "" : m.name()).append(" | ").append(d.accessLabel(e)).append(" |\n");
            if (src.size() < 6) src.add(new Source(e.file(), e.line(), e.line(), e.httpMethod() + " " + e.path(), "endpoint"));
        }
        return new Draft(sb.toString().trim(), src, null, List.of(new Action("Browse APIs", "apis", null)), "ENDPOINTS");
    }

    private Draft overview() {
        Overview o = k.result.overview();
        StringBuilder sb = new StringBuilder("### Project overview\n\n").append(o.summary()).append("\n\n");
        sb.append("**Tech stack:** ").append(String.join(", ", concat(o.frameworks(), o.databases(), o.tools()))).append("\n\n");
        sb.append("**Languages:** ").append(o.languages().entrySet().stream().limit(5).map(e -> e.getKey() + " " + e.getValue() + "%")
                .collect(Collectors.joining(", "))).append("\n\n");
        for (String h : o.highlights()) sb.append("- ").append(h).append('\n');
        sb.append("\n**Modules**\n");
        for (ModuleInfo m : k.result.modules()) sb.append("- **").append(m.name()).append("** — ").append(m.description().replaceFirst("^Module “[^”]*”: ", "")).append('\n');
        List<Source> src = new ArrayList<>();
        k.result.classes().stream().filter(c -> "APPLICATION".equals(c.layer())).findFirst()
                .ifPresent(c -> src.add(new Source(c.file(), c.line(), c.endLine(), c.name(), "application")));
        return new Draft(sb.toString().trim(), src, null, List.of(new Action("Open architecture diagram", "architecture", null)), "OVERVIEW");
    }

    @SafeVarargs
    private static List<String> concat(List<String>... lists) {
        List<String> out = new ArrayList<>();
        for (List<String> l : lists) for (String s : l) if (!out.contains(s)) out.add(s);
        return out;
    }

    private Draft dependencies(ClassInfo c) {
        StringBuilder sb = new StringBuilder("### Dependencies of `" + c.name() + "`\n\n").append(d.classSummary(c)).append("\n\n");
        List<String> uses = new ArrayList<>();
        for (Dep dep : c.dependsOn()) {
            ClassInfo t = k.classById.get(dep.target());
            if (t != null && !t.test()) uses.add("`" + t.name() + "` (" + depLabel(dep.kind()) + ")");
        }
        sb.append("**It depends on:** ").append(uses.isEmpty() ? "nothing else in the project" : String.join(", ", uses)).append("\n\n");
        List<String> by = k.dependentsOf(c.id()).stream().map(x -> "`" + x.name() + "`").toList();
        sb.append("**It is used by:** ").append(by.isEmpty() ? "no other class" : String.join(", ", by));
        List<Source> src = List.of(new Source(c.file(), c.line(), c.endLine(), c.name(), "class"));
        return new Draft(sb.toString(), src, null, List.of(new Action("Open dependency tree", "dependencies", c.id())), "DEPENDENCIES");
    }

    private static String depLabel(String k) {
        return switch (k) {
            case "INJECTS" -> "injected";
            case "CALLS" -> "calls";
            case "EXTENDS" -> "extends";
            case "IMPLEMENTS" -> "implements";
            default -> "uses";
        };
    }

    private Draft explainClass(ClassInfo c, String ql) {
        Explanation e = d.classExplanation(c);
        StringBuilder sb = new StringBuilder("### `" + c.name() + "`\n\n").append(e.summary()).append("\n\n");
        for (String f : e.facts()) sb.append("- ").append(f).append('\n');
        List<Link> uses = e.links().stream().filter(l -> "Uses".equals(l.group())).toList();
        List<Link> usedBy = e.links().stream().filter(l -> "Used by".equals(l.group())).toList();
        if (!uses.isEmpty()) sb.append("\n**Uses:** ").append(uses.stream().map(l -> "`" + l.label() + "`").collect(Collectors.joining(", ")));
        if (!usedBy.isEmpty()) sb.append("\n\n**Used by:** ").append(usedBy.stream().map(l -> "`" + l.label() + "`").collect(Collectors.joining(", ")));
        List<MethodInfo> ms = c.methods().stream().filter(m -> !m.constructor()).limit(8).toList();
        if (!ms.isEmpty()) {
            sb.append("\n\n**Key methods**\n");
            for (MethodInfo m : ms) sb.append("- `").append(m.name()).append("` — ").append(d.methodSummary(c, m)).append('\n');
        }
        List<Source> src = new ArrayList<>();
        src.add(new Source(c.file(), c.line(), Math.min(c.endLine(), c.line() + 30), c.name(), "class"));
        return new Draft(sb.toString().trim(), src, null, List.of(new Action("Open dependency tree", "dependencies", c.id())), "EXPLAIN");
    }

    private record MethodHit(ClassInfo c, MethodInfo m) {}

    private Draft explainMethod(MethodHit h) {
        Explanation e = d.methodExplanation(h.c(), h.m());
        StringBuilder sb = new StringBuilder("### `" + h.c().name() + "." + h.m().name() + "()`\n\n").append(e.summary()).append("\n\n");
        for (String f : e.facts()) sb.append("- ").append(f).append('\n');
        List<Link> calls = e.links().stream().filter(l -> "Calls".equals(l.group())).toList();
        List<Link> by = e.links().stream().filter(l -> "Called by".equals(l.group())).toList();
        if (!calls.isEmpty()) sb.append("\n**It calls:** ").append(calls.stream().map(l -> "`" + l.label() + "`").collect(Collectors.joining(", ")));
        if (!by.isEmpty()) sb.append("\n\n**Called by:** ").append(by.stream().map(l -> "`" + l.label() + "`").collect(Collectors.joining(", ")));
        List<Source> src = List.of(new Source(h.c().file(), h.m().line(), h.m().endLine(), h.c().name() + "." + h.m().name(), "method"));
        Flow flow = null;
        List<Action> acts = new ArrayList<>();
        List<EndpointInfo> eps = k.endpointsByHandler.getOrDefault(h.m().id(), List.of());
        if (!eps.isEmpty()) {
            flow = k.flows().endpointFlow(eps.get(0).id());
            acts.add(new Action("Open interactive flow", "flow", eps.get(0).id()));
        }
        return new Draft(sb.toString().trim(), src, flow, acts, "EXPLAIN");
    }

    private Draft fromSelection(String q, Selection sel) {
        ClassInfo c = null;
        for (ClassInfo x : k.result.classes()) {
            if (x.file().equals(sel.file()) && sel.startLine() >= x.line() && sel.startLine() <= x.endLine()
                    && (c == null || x.endLine() - x.line() < c.endLine() - c.line())) c = x;
        }
        if (c == null) {
            Explanation e = d.fileExplanation(sel.file());
            return new Draft("### " + e.title() + "\n\n" + e.summary(), List.of(new Source(sel.file(), sel.startLine(), sel.endLine(), e.title(), "file")),
                    null, List.of(), "SELECTION");
        }
        MethodInfo m = k.methodAtLine(c, sel.startLine());
        StringBuilder sb = new StringBuilder("### Selected code in `" + c.name() + (m == null ? "" : "." + m.name() + "()") + "`\n\n");
        boolean refactor = q.toLowerCase(Locale.ROOT).contains("refactor");
        if (m != null) {
            sb.append(d.methodSummary(c, m)).append("\n\n");
            List<String> calls = m.calls().stream().filter(x -> x.line() >= sel.startLine() && x.line() <= sel.endLine())
                    .map(x -> "`" + x.targetClassName() + "." + x.method() + "`").distinct().toList();
            if (!calls.isEmpty()) sb.append("Within your selection it calls ").append(String.join(", ", calls)).append(".\n\n");
        } else sb.append(d.classSummary(c)).append("\n\n");
        if (refactor) {
            sb.append("**Refactoring ideas**\n");
            if (m != null && m.loc() > 40) sb.append("- This method is ").append(m.loc()).append(" lines long — split it into smaller private methods named after what each part does.\n");
            if ("CONTROLLER".equals(c.layer()) && m != null && m.calls().stream().anyMatch(x -> {
                ClassInfo t = k.classById.get(x.targetClass());
                return t != null && "REPOSITORY".equals(t.layer());
            })) sb.append("- The controller talks to a repository directly; move that logic into a service to keep controllers thin.\n");
            sb.append("- Prefer descriptive names and keep one responsibility per method.\n");
        }
        return new Draft(sb.toString().trim(), List.of(new Source(sel.file(), sel.startLine(), sel.endLine(), "Selection", "selection")), null, List.of(), "SELECTION");
    }

    private Draft search(String q) {
        List<VectorIndex.Hit> hits = k.vectors().search(q, 6, c -> !"file".equals(c.type()) || c.file() != null);
        if (hits.isEmpty() || hits.get(0).score() < 0.1) {
            return new Draft("I couldn't find anything in this project that clearly matches that.\n\nTry mentioning a **class**, **method**, **endpoint** or **table** name — "
                    + "for example *“Explain " + sampleClass() + "”*, *“How does " + sampleEndpoint() + " work?”* or *“How is security implemented?”*.",
                    List.of(), null, List.of(), "SEARCH");
        }
        StringBuilder sb = new StringBuilder("### Most relevant code\n\n");
        List<Source> src = new ArrayList<>();
        int n = 0;
        for (VectorIndex.Hit h : hits) {
            Chunk c = h.chunk();
            if (c.file() == null || n >= 5) continue;
            if ("module".equals(c.type()) || "layer".equals(c.type()) || "overview".equals(c.type())) {
                continue;
            }
            n++;
            String what = c.methodId() != null && k.methodById.containsKey(c.methodId())
                    ? d.methodSummary(k.classOf(k.methodById.get(c.methodId())), k.methodById.get(c.methodId()))
                    : c.classId() != null && k.classById.containsKey(c.classId()) ? d.classSummary(k.classById.get(c.classId()))
                    : firstLines(c.text());
            sb.append(n).append(". **").append(c.name()).append("** (`").append(c.file().substring(c.file().lastIndexOf('/') + 1)).append(':')
                    .append(c.startLine()).append("`) — ").append(what).append('\n');
            src.add(new Source(c.file(), c.startLine(), c.endLine(), c.name(), c.type()));
        }
        if (n == 0) return new Draft("I couldn't find a specific place for that. Try naming a class, method or endpoint.", List.of(), null, List.of(), "SEARCH");
        return new Draft(sb.toString().trim(), src, null, List.of(), "SEARCH");
    }

    private static String firstLines(String text) {
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            String t = l.trim();
            if (t.startsWith("//") || t.isEmpty()) continue;
            sb.append(t).append(' ');
            if (sb.length() > 140) break;
        }
        return sb.toString().trim();
    }

    // ═════════════════════════ linking ═════════════════════════

    private List<ClassInfo> linkClasses(String ql) {
        List<ClassInfo> out = new ArrayList<>();
        String squashed = ql.replaceAll("[^a-z0-9]", "");
        Set<String> qt = new HashSet<>(TypeUtil.words(ql));
        Map<ClassInfo, Integer> score = new HashMap<>();
        for (ClassInfo c : k.result.classes()) {
            if (c.test() || c.nested()) continue;
            String lower = c.name().toLowerCase(Locale.ROOT);
            List<String> w = TypeUtil.words(c.name());
            int s = 0;
            if (Pattern.compile("\\b" + Pattern.quote(lower) + "\\b").matcher(ql).find()) s = 10 + w.size();
            else if (w.size() >= 2 && squashed.contains(lower)) s = 8 + w.size();
            else if (w.size() >= 2 && qt.containsAll(w)) s = 6 + w.size();
            else if (w.size() == 1 && w.get(0).length() > 3 && qt.contains(w.get(0)) && !isGeneric(w.get(0))) s = 3;
            if (s > 0) score.put(c, s);
        }
        score.entrySet().stream().sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())).limit(4).forEach(e -> out.add(e.getKey()));
        return out;
    }

    private static boolean isGeneric(String w) {
        return Set.of("user", "data", "file", "base", "model", "name", "list", "type", "info", "main", "config", "service", "controller", "repository").contains(w);
    }

    private List<MethodHit> linkMethods(String ql) {
        List<MethodHit> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ClassInfo c : k.result.classes()) {
            if (c.test()) continue;
            for (MethodInfo m : c.methods()) {
                if (m.constructor() || m.name().length() < 5) continue;
                String lower = m.name().toLowerCase(Locale.ROOT);
                if (TypeUtil.words(m.name()).size() >= 2 && Pattern.compile("\\b" + Pattern.quote(lower) + "\\b").matcher(ql).find() && seen.add(m.name())) {
                    out.add(new MethodHit(c, m));
                }
            }
        }
        // prefer a method that lives in a class also mentioned in the question
        List<ClassInfo> linked = linkClasses(ql);
        out.sort((a, b) -> Boolean.compare(linked.contains(b.c()), linked.contains(a.c())));
        return out;
    }

    private String sampleClass() {
        return k.result.classes().stream().filter(c -> !c.test() && "CONTROLLER".equals(c.layer())).map(ClassInfo::name).findFirst()
                .orElse(k.result.classes().isEmpty() ? "a class" : k.result.classes().get(0).name());
    }

    private String sampleEndpoint() {
        return k.result.endpoints().isEmpty() ? "an endpoint" : k.result.endpoints().get(0).httpMethod() + " " + k.result.endpoints().get(0).path();
    }

    private static List<Source> dedupe(List<Source> in) {
        List<Source> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Source s : in) if (seen.add(s.file() + ":" + s.startLine())) out.add(s);
        return out.size() > 10 ? out.subList(0, 10) : out;
    }

    // ═════════════════════════ suggestions ═════════════════════════

    public List<String> suggestions() {
        List<String> out = new ArrayList<>();
        out.add("Give me an overview of this project");
        if (!k.result.endpoints().isEmpty()) {
            EndpointInfo e = k.result.endpoints().get(0);
            out.add("How does " + e.httpMethod() + " " + e.path() + " work?");
        }
        k.result.classes().stream().filter(c -> !c.test() && "CONTROLLER".equals(c.layer())).findFirst()
                .ifPresent(c -> out.add("Explain " + c.name()));
        if (k.result.security() != null && k.result.security().enabled()) out.add("How is security implemented?");
        else out.add("Is there any authentication in this project?");
        if (k.result.schema() != null && !k.result.schema().tables().isEmpty()) out.add("Show me the database schema");
        out.add("List all API endpoints");
        return out;
    }

    // ═════════════════════════ LLM prompt ═════════════════════════

    private String systemPrompt() {
        Overview o = k.result.overview();
        return "You are CodeTutor, a friendly senior engineer who helps students understand an unfamiliar codebase.\n"
                + "Answer ONLY from the verified analysis and code excerpts provided in the user message; never invent classes, files or behaviour.\n"
                + "Write beginner-friendly markdown, at most ~350 words, using short paragraphs and bullet lists. Mention file names with line numbers like `OwnerController.java:42` when useful.\n"
                + "Project: " + o.name() + ". " + o.summary();
    }

    private List<LlmClient.Turn> turns(List<LlmClient.Turn> history, String q, Draft draft) {
        List<LlmClient.Turn> t = new ArrayList<>();
        if (history != null) for (LlmClient.Turn h : history.stream().skip(Math.max(0, history.size() - 6)).toList()) t.add(h);
        StringBuilder ctx = new StringBuilder();
        ctx.append("Verified analysis (from static analysis of the code — trust this):\n").append(draft.text()).append("\n\n");
        ctx.append("Relevant code excerpts:\n");
        int budget = 9000;
        for (VectorIndex.Hit h : k.vectors().search(q, 5, c -> c.file() != null)) {
            String text = h.chunk().text();
            if (text.length() > 1800) text = text.substring(0, 1800);
            if (budget - text.length() < 0) break;
            budget -= text.length();
            ctx.append("--- ").append(h.chunk().file()).append(':').append(h.chunk().startLine()).append('\n').append(text).append("\n");
        }
        ctx.append("\nQuestion: ").append(q).append("\nAnswer the question using the material above.");
        t.add(new LlmClient.Turn("user", ctx.toString()));
        return t;
    }
}
