package com.codetutor.backend.flow;

import com.codetutor.backend.analysis.Knowledge;
import com.codetutor.backend.analysis.model.AnalysisModel.*;
import com.codetutor.backend.analysis.util.TypeUtil;
import com.codetutor.backend.explain.Describer;
import com.codetutor.backend.search.VectorIndex;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Generates data-flow diagrams (as ordered step trees) from the analysed call graph: how a request travels from the
 * browser through the security filters, controller, services and repositories down to the database tables.
 */
public class FlowService {

    private static final int MAX_DEPTH = 7;
    private static final int MAX_STEPS = 48;

    private final Knowledge k;
    private final Describer d;

    public FlowService(Knowledge k, Describer d) {
        this.k = k;
        this.d = d;
    }

    // ───────────────────────── endpoint flow ─────────────────────────

    public Flow endpointFlow(String endpointId) {
        EndpointInfo ep = k.endpointById.get(endpointId);
        if (ep == null) return null;
        ClassInfo ctrl = k.classById.get(ep.controller());
        MethodInfo handler = k.methodById.get(ep.handler());
        List<FlowStep> steps = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        // 0. client
        JsFile origin = null;
        ApiCall originCall = null;
        if (k.result.frontend() != null) {
            for (JsFile f : k.result.frontend().files()) {
                for (ApiCall a : f.apiCalls()) {
                    if (endpointId.equals(a.endpointId())) { origin = f; originCall = a; break; }
                }
                if (origin != null) break;
            }
        }
        int last;
        if (origin != null) {
            last = add(steps, -1, "CLIENT", "React UI", origin.path().substring(origin.path().lastIndexOf('/') + 1),
                    origin.path(), originCall.line(), originCall.line(),
                    "The front-end (" + origin.path() + ") sends " + ep.httpMethod() + " " + ep.fullPath() + " to the server.",
                    null, null, null, null);
        } else {
            last = add(steps, -1, "CLIENT", "Client", ep.httpMethod() + " " + ep.fullPath(), null, 0, 0,
                    "A browser or API client sends " + ep.httpMethod() + " " + ep.fullPath() + "."
                            + (ep.params().isEmpty() ? "" : " Input: " + ep.params().stream()
                            .map(p -> p.name() + " (" + p.source() + ")").collect(Collectors.joining(", ")) + "."),
                    null, null, null, null);
        }

        // 1. security chain
        Security sec = k.result.security();
        String access = ep.access() == null ? "PUBLIC" : ep.access();
        if (sec != null && sec.enabled()) {
            for (SecurityFilter f : sec.filters()) {
                boolean authz = f.name().equals("AuthorizationFilter");
                if (authz) {
                    last = add(steps, last, "SECURITY", "Authorization check", describeAccess(access, sec, ep), f.file(),
                            Math.max(f.line(), 1), Math.max(f.line(), 1), accessSentence(access, sec, ep), "then", f.classId(), null,
                            access);
                } else {
                    last = add(steps, last, "FILTER", f.name(), "security filter " + f.order(), f.file(), Math.max(f.line(), 1),
                            Math.max(f.line(), 1), f.description(), "passes through", f.classId(), null, null);
                }
            }
            if ("PUBLIC".equals(access)) notes.add("This endpoint is public — no token or login is required.");
            else notes.add("A request without valid credentials is rejected by the security filters before it reaches the controller.");
        } else {
            notes.add("No security configuration was detected, so this endpoint is open to everyone.");
        }

        // 2. controller + call tree
        int ctrlIdx = last;
        if (ctrl != null && handler != null) {
            ctrlIdx = add(steps, last, "CONTROLLER", ctrl.name() + "." + handler.name() + "()", ctrl.name(), ctrl.file(), handler.line(),
                    handler.endLine(), "Spring routes the request here. " + d.methodSummary(ctrl, handler), "dispatches to", ctrl.id(),
                    handler.id(), access);
            Set<String> path = new HashSet<>();
            path.add(handler.id());
            walk(steps, handler, ctrlIdx, 1, path);
        }

        // 3. response
        String resp = ep.viewResponse()
                ? "The controller returns the name of an HTML page; the view engine renders it with the model data and sends the page back."
                : "The result" + (ep.returns() == null || "void".equals(ep.returns()) ? "" : " (" + ep.returns() + ")")
                + " is converted to JSON and sent back with the HTTP status.";
        add(steps, ctrlIdx, "RESPONSE", ep.viewResponse() ? "HTML page" : "HTTP response",
                ep.viewResponse() ? "rendered view" : (ep.returns() == null ? "JSON" : ep.returns()), null, 0, 0, resp, "returns", null, null, null);

        long tables = steps.stream().filter(s -> "TABLE".equals(s.kind())).map(FlowStep::title).distinct().count();
        if (tables > 0) {
            notes.add("Touches " + tables + " database table" + (tables > 1 ? "s" : "") + ": " + steps.stream()
                    .filter(s -> "TABLE".equals(s.kind())).map(FlowStep::title).distinct().collect(Collectors.joining(", ")) + ".");
        }
        Flow flow = new Flow(endpointId, ep.httpMethod() + " " + ep.fullPath(),
                (ctrl == null ? "" : ctrl.name()) + (handler == null ? "" : "." + handler.name() + "()"), "endpoint", steps, notes, null);
        return new Flow(flow.id(), flow.title(), flow.subtitle(), flow.kind(), flow.steps(), flow.notes(), narrative(flow));
    }

    private void walk(List<FlowStep> steps, MethodInfo m, int parent, int depth, Set<String> path) {
        if (depth > MAX_DEPTH) return;
        Set<String> siblings = new HashSet<>();
        for (Call call : m.calls().stream().sorted(Comparator.comparingInt(Call::line)).toList()) {
            if (steps.size() >= MAX_STEPS) return;
            ClassInfo tc = k.classById.get(call.targetClass());
            if (tc == null) continue;
            if (call.library() || call.targetMethodId() == null) {
                String key = tc.id() + "#" + call.method();
                if (!siblings.add(key)) continue;
                boolean repo = "REPOSITORY".equals(tc.layer());
                String kind = repo ? "REPOSITORY" : tc.layer();
                String op = sqlOp(call.method());
                int idx = add(steps, parent, kind, tc.name() + "." + call.method() + "()", repo ? "Spring Data JPA" : tc.name(),
                        tc.file(), tc.line(), tc.line(), repo ? "Spring Data generates this query method: " + op + sqlTarget(tc) + "."
                                : "Calls " + tc.name() + "." + call.method() + ".", verbLabel(call.method(), repo), tc.id(), null, null);
                if (repo) addTable(steps, idx, tc, call.method());
                continue;
            }
            MethodInfo target = k.methodById.get(call.targetMethodId());
            if (target == null) continue;
            for (MethodInfo tm : k.concreteTargets(target)) {
                if (steps.size() >= MAX_STEPS) return;
                if (path.contains(tm.id())) continue;
                if (!siblings.add(tm.id())) continue;
                ClassInfo owner = k.classOf(tm);
                if (owner == null) continue;
                String kind = owner.layer();
                boolean impl = !target.id().equals(tm.id());
                int idx = add(steps, parent, kind, owner.name() + "." + tm.name() + "()", impl ? owner.name() + " (implements "
                                + TypeUtil.simpleName(target.id().substring(0, target.id().indexOf('#'))) + ")" : owner.name(),
                        owner.file(), tm.line(), tm.endLine(), d.methodSummary(owner, tm), verbLabel(tm.name(), "REPOSITORY".equals(kind)),
                        owner.id(), tm.id(), null);
                if ("REPOSITORY".equals(owner.layer())) addTable(steps, idx, owner, tm.name());
                path.add(tm.id());
                walk(steps, tm, idx, depth + 1, path);
                path.remove(tm.id());
            }
        }
    }

    private void addTable(List<FlowStep> steps, int parent, ClassInfo repo, String method) {
        String table = k.tableOfRepository(repo);
        if (table == null) return;
        Table t = k.tableByName.get(table);
        boolean write = isWrite(method);
        add(steps, parent, "TABLE", table, t == null ? "database table" : t.columns().size() + " columns",
                t == null ? null : t.file(), t == null ? 0 : t.line(), t == null ? 0 : t.line(),
                (write ? "Rows are inserted/updated/deleted in table `" : "Rows are read from table `") + table + "`.",
                write ? "writes" : "reads", t == null ? null : t.entity(), null, null);
    }

    private static boolean isWrite(String m) {
        return m.matches("^(save|delete|insert|update|remove|persist|merge|flush|create|add|store).*");
    }

    private static String sqlOp(String m) {
        if (m.startsWith("save")) return "INSERT or UPDATE";
        if (m.startsWith("delete") || m.startsWith("remove")) return "DELETE";
        if (m.startsWith("count")) return "SELECT COUNT";
        if (m.startsWith("exists")) return "SELECT EXISTS";
        return "SELECT";
    }

    private String sqlTarget(ClassInfo repo) {
        String t = k.tableOfRepository(repo);
        return t == null ? "" : " on table `" + t + "`";
    }

    private static String verbLabel(String method, boolean repo) {
        if (repo) return isWrite(method) ? "saves via" : "queries";
        return "calls";
    }

    private String describeAccess(String access, Security sec, EndpointInfo ep) {
        return switch (access.split(":")[0]) {
            case "PUBLIC" -> "permitAll";
            case "AUTHENTICATED" -> "login required";
            case "ROLE" -> "role " + access.substring(5);
            case "DENIED" -> "denied";
            default -> access.toLowerCase(Locale.ROOT);
        };
    }

    private String accessSentence(String access, Security sec, EndpointInfo ep) {
        String rule = sec.rules().stream().filter(r -> matches(r, ep)).map(r -> r.pattern() + " → " + r.access()).findFirst()
                .orElse("default rule");
        return switch (access.split(":")[0]) {
            case "PUBLIC" -> "Matched rule “" + rule + "”: the request may continue without authentication.";
            case "AUTHENTICATED" -> "Matched rule “" + rule + "”: only authenticated users may continue; others get 401 Unauthorized.";
            case "ROLE" -> "Matched rule “" + rule + "”: the user must have the role " + access.substring(5) + "; otherwise 403 Forbidden.";
            case "DENIED" -> "Matched rule “" + rule + "”: nobody is allowed.";
            default -> "Matched rule “" + rule + "”.";
        };
    }

    private boolean matches(AuthRule r, EndpointInfo ep) {
        if (r.httpMethod() != null && !r.httpMethod().equalsIgnoreCase(ep.httpMethod())) return false;
        org.springframework.util.AntPathMatcher m = new org.springframework.util.AntPathMatcher();
        for (String p : r.pattern().split(" , ")) if (p.trim().equals("/**") || m.match(p.trim(), ep.path())) return true;
        return false;
    }

    private static int add(List<FlowStep> steps, int parent, String kind, String title, String subtitle, String file, int line,
                           int endLine, String desc, String edge, String classId, String methodId, String access) {
        int idx = steps.size();
        steps.add(new FlowStep(idx, parent, kind, title, subtitle, file, line, endLine, desc, edge, classId, methodId, access));
        return idx;
    }

    // ───────────────────────── security flow ─────────────────────────

    public Flow securityFlow() {
        Security sec = k.result.security();
        List<FlowStep> steps = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        int last = add(steps, -1, "CLIENT", "Incoming request", "browser / API client", null, 0, 0,
                "Every HTTP request first meets the security layer before any controller code runs.", null, null, null, null);
        if (sec == null || !sec.enabled()) {
            add(steps, last, "CONTROLLER", "Controller", "no security configured", null, 0, 0,
                    "No Spring Security configuration was detected, so requests go straight to the controllers.", "reaches", null, null, null);
            notes.add("There is no authentication or authorization in this project.");
            Flow f = new Flow("security", "Security flow", "none detected", "security", steps, notes, null);
            return new Flow(f.id(), f.title(), f.subtitle(), f.kind(), f.steps(), f.notes(), narrative(f));
        }
        for (SecurityFilter f : sec.filters()) {
            boolean authz = f.name().equals("AuthorizationFilter");
            if (authz) {
                String rules = sec.rules().stream().limit(8).map(r -> (r.httpMethod() == null ? "" : r.httpMethod() + " ") + r.pattern() + " → "
                        + r.access()).collect(Collectors.joining("\n"));
                last = add(steps, last, "SECURITY", "Authorization rules", sec.rules().size() + " rules", f.file(), Math.max(1, f.line()),
                        Math.max(1, f.line()), "Rules are checked top to bottom; the first match wins:\n" + rules, "then", f.classId(), null, null);
            } else {
                last = add(steps, last, "FILTER", f.name(), "filter " + f.order(), f.file(), Math.max(1, f.line()), Math.max(1, f.line()),
                        f.description(), "passes through", f.classId(), null, null);
            }
        }
        add(steps, last, "CONTROLLER", "Controller method runs", "access granted", null, 0, 0,
                "If the rule allows the request, Spring dispatches it to the matching controller method.", "allowed", null, null, null);
        add(steps, last, "RESPONSE", "401 / 403 response", "access denied", null, 0, 0,
                "Otherwise the request is stopped here: 401 Unauthorized (not logged in) or 403 Forbidden (wrong role).", "denied", null, null, null);
        notes.addAll(sec.notes());
        if (sec.passwordEncoder() != null) notes.add("Passwords are stored hashed with " + sec.passwordEncoder() + ".");
        Flow f = new Flow("security", "Security flow", sec.summary(), "security", steps, notes, null);
        return new Flow(f.id(), f.title(), f.subtitle(), f.kind(), f.steps(), f.notes(), narrative(f));
    }

    // ───────────────────────── matching endpoints to a question ─────────────────────────

    private static final Pattern EXPLICIT = Pattern.compile("\\b(GET|POST|PUT|DELETE|PATCH)\\s+(/\\S*)", Pattern.CASE_INSENSITIVE);

    public List<EndpointInfo> matchEndpoints(String question, int max) {
        List<EndpointInfo> eps = k.result.endpoints();
        if (eps.isEmpty()) return List.of();
        Matcher m = EXPLICIT.matcher(question);
        if (m.find()) {
            String method = m.group(1).toUpperCase(Locale.ROOT);
            String path = m.group(2).replaceAll("[?.,;:!]+$", "");
            List<EndpointInfo> exact = eps.stream().filter(e -> e.httpMethod().equals(method)
                    && (e.path().equals(path) || e.fullPath().equals(path))).toList();
            if (!exact.isEmpty()) return exact.subList(0, Math.min(max, exact.size()));
        }
        Set<String> q = new HashSet<>(VectorIndex.tokens(question));
        Map<EndpointInfo, Double> score = new HashMap<>();
        for (EndpointInfo e : eps) {
            Set<String> name = new HashSet<>();
            name.addAll(VectorIndex.tokens(e.path().replace('{', ' ').replace('}', ' ')));
            ClassInfo c = k.classById.get(e.controller());
            MethodInfo h = k.methodById.get(e.handler());
            if (h != null) name.addAll(VectorIndex.tokens(h.name()));
            if (c != null) name.addAll(VectorIndex.tokens(c.name()));
            double s = 0;
            for (String t : name) if (q.contains(t)) s += 1;
            if (h != null) for (String t : VectorIndex.tokens(h.name())) if (q.contains(t)) s += 0.5;
            // explicit method word in question
            if (question.toLowerCase(Locale.ROOT).contains(e.httpMethod().toLowerCase(Locale.ROOT) + " ")) s += 0.5;
            if (s > 0) score.put(e, s);
        }
        return score.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .filter(en -> en.getValue() >= 1.5).limit(max).map(Map.Entry::getKey).toList();
    }

    // ───────────────────────── narrative ─────────────────────────

    public String narrative(Flow f) {
        StringBuilder sb = new StringBuilder();
        int n = 1;
        List<FlowStep> filters = f.steps().stream().filter(s -> "FILTER".equals(s.kind())).toList();
        boolean filtersDone = false;
        for (FlowStep s : f.steps()) {
            if ("FILTER".equals(s.kind())) {
                if (filtersDone) continue;
                filtersDone = true;
                sb.append(n++).append(". **Security filters** — the request passes through ")
                        .append(filters.stream().map(x -> "`" + x.title() + "`").collect(Collectors.joining(" → "))).append(".\n");
                continue;
            }
            if ("TABLE".equals(s.kind())) {
                sb.append("   - ").append("reads".equals(s.edgeLabel()) ? "reads from" : "writes to").append(" table `").append(s.title()).append("`\n");
                continue;
            }
            sb.append(n++).append(". **").append(s.title().replace("*", "")).append("**");
            if (s.file() != null && s.line() > 0) sb.append(" (`").append(s.file().substring(s.file().lastIndexOf('/') + 1)).append(":").append(s.line()).append("`)");
            sb.append(" — ").append(s.description() == null ? "" : s.description().split("\n")[0]).append('\n');
        }
        return sb.toString().trim();
    }
}
