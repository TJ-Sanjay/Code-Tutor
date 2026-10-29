package com.codetutor.backend.analysis.python;

import com.codetutor.backend.analysis.model.AnalysisModel.*;
import com.codetutor.backend.analysis.python.PyModel.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Django (and Flask / FastAPI decorator-route) knowledge: reads settings.py, follows the URL configuration to build
 * the endpoint list (function views, class-based views, DRF generics and routers), derives the database schema from
 * model classes and describes the security set-up (middleware chain, authentication, permissions).
 */
class DjangoSupport {

    private final PythonAnalyzer a;

    DjangoSupport(PythonAnalyzer a) { this.a = a; }

    // ═════════════════════════ settings ═════════════════════════

    private final Map<String, List<Sett>> settings = new LinkedHashMap<>();
    private PF settingsFile;
    private final Set<String> frameworkSet = new LinkedHashSet<>();
    private final Set<String> toolSet = new LinkedHashSet<>();
    private final List<String> notes = new ArrayList<>();

    private static final Pattern SET_ASSIGN = Pattern.compile("^([A-Z][A-Z0-9_]*)\\s*(\\+?=)\\s*(.*)$", Pattern.DOTALL);
    private static final Pattern SET_MUTATE = Pattern.compile("^([A-Z][A-Z0-9_]*)\\.(append|insert|extend)\\((.*)\\)$", Pattern.DOTALL);

    void parseSettings() {
        List<PF> cand = new ArrayList<>();
        for (PF pf : a.files.values()) {
            String p = pf.path.toLowerCase(Locale.ROOT);
            if (pf.test) continue;
            if (pf.stem().equals("settings") || p.contains("/settings/") || p.startsWith("settings/")) cand.add(pf);
        }
        cand.sort(Comparator.comparingInt((PF pf) -> rank(pf)).thenComparing(pf -> pf.path));
        for (PF pf : cand) {
            for (PyParser.Stmt st : pf.mod.top) {
                Matcher m = SET_ASSIGN.matcher(st.text());
                if (m.matches()) {
                    settings.computeIfAbsent(m.group(1), k -> new ArrayList<>()).add(new Sett(m.group(3), pf, st.line()));
                    continue;
                }
                Matcher mm = SET_MUTATE.matcher(st.text());
                if (mm.matches()) settings.computeIfAbsent(mm.group(1), k -> new ArrayList<>()).add(new Sett(mm.group(3), pf, st.line()));
            }
        }
        for (PF pf : cand) if (settings.containsKey("INSTALLED_APPS") && hasSetting(pf, "INSTALLED_APPS")) { settingsFile = pf; break; }
        if (settingsFile == null && !cand.isEmpty()) settingsFile = cand.get(0);
        List<String> apps = settingStrings("INSTALLED_APPS");
        if (apps.stream().anyMatch(s -> s.startsWith("rest_framework"))) frameworkSet.add("Django REST Framework");
        for (PF pf : a.files.values()) {
            for (PyParser.Imp i : pf.mod.imports) {
                String m = i.module() == null ? "" : i.module();
                if (m.startsWith("rest_framework") && !m.startsWith("rest_framework_simplejwt")) frameworkSet.add("Django REST Framework");
                if (m.startsWith("rest_framework_simplejwt")) toolSet.add("JWT");
                if (m.equals("celery") || m.startsWith("celery.")) toolSet.add("Celery");
                if (m.equals("channels") || m.startsWith("channels.")) toolSet.add("Django Channels");
                if (m.startsWith("django_filters")) toolSet.add("django-filter");
            }
        }
        if (apps.stream().anyMatch(s -> s.startsWith("corsheaders"))) toolSet.add("django-cors-headers");
        if (apps.stream().anyMatch(s -> s.startsWith("drf_spectacular") || s.startsWith("drf_yasg"))) toolSet.add("OpenAPI / Swagger");
        if (apps.stream().anyMatch(s -> s.startsWith("rest_framework_simplejwt"))) toolSet.add("JWT");
    }

    private int rank(PF pf) {
        String s = pf.stem();
        if (s.equals("settings")) return 0;
        if (s.equals("base") || s.equals("common")) return 1;
        return 2 + s.length();
    }

    private boolean hasSetting(PF pf, String name) {
        for (Sett s : settings.getOrDefault(name, List.of())) if (s.file == pf) return true;
        return false;
    }

    Sett setting(String name) {
        List<Sett> l = settings.get(name);
        return l == null || l.isEmpty() ? null : l.get(0);
    }

    List<String> settingStrings(String name) {
        List<String> out = new ArrayList<>();
        for (Sett s : settings.getOrDefault(name, List.of())) out.addAll(PyParser.strings(s.value));
        return out;
    }

    private String settingLiteral(String name) {
        Sett s = setting(name);
        return s == null ? null : PyParser.literal(s.value);
    }

    String projectPackage() {
        if (settingsFile == null) return null;
        String d = settingsFile.dotted;
        int i = d.lastIndexOf('.');
        String parent = i < 0 ? d : d.substring(0, i);
        if (parent.endsWith(".settings")) parent = parent.substring(0, parent.length() - ".settings".length());
        return parent;
    }

    List<String> frameworks() {
        List<String> out = new ArrayList<>();
        if (a.django) out.add("Django");
        out.addAll(frameworkSet);
        return out;
    }

    List<String> tools() { return new ArrayList<>(toolSet); }

    List<String> databases() {
        List<String> out = new ArrayList<>();
        Sett db = setting("DATABASES");
        if (db == null) return out;
        Matcher m = Pattern.compile("['\"]ENGINE['\"]\\s*:\\s*['\"]([^'\"]+)['\"]").matcher(db.value);
        while (m.find()) {
            String e = m.group(1).toLowerCase(Locale.ROOT);
            String n = e.contains("postgres") || e.contains("postgis") ? "PostgreSQL" : e.contains("mysql") ? "MySQL"
                    : e.contains("sqlite") ? "SQLite" : e.contains("oracle") ? "Oracle" : e.contains("mssql") || e.contains("sql_server") ? "SQL Server"
                    : e.contains("mongo") ? "MongoDB" : null;
            if (n != null && !out.contains(n)) out.add(n);
        }
        return out;
    }

    void reclassifyMiddleware() {
        for (String dotted : settingStrings("MIDDLEWARE")) {
            int i = dotted.lastIndexOf('.');
            if (i < 0) continue;
            Cx c = classAt(dotted.substring(0, i), dotted.substring(i + 1));
            if (c == null || c.test) continue;
            String n = c.name.toLowerCase(Locale.ROOT);
            c.attrs.put("djangoKind", "middleware");
            c.layer = n.matches(".*(auth|jwt|token|permission|csrf|security|cors|ratelimit|throttle).*") ? "SECURITY" : "COMPONENT";
        }
    }

    private Cx classAt(String module, String name) {
        PF pf = a.moduleByDotted(module);
        if (pf == null) return null;
        for (Cx c : pf.classes) if (c.name.equals(name)) return c;
        return null;
    }

    // ═════════════════════════ model helpers ═════════════════════════

    private static boolean isModel(Cx c) { return c != null && "ENTITY".equals(c.layer) && "model".equals(c.attrs.get("djangoKind")); }

    Cx userModel() {
        String lit = settingLiteral("AUTH_USER_MODEL");
        if (lit == null || lit.equals("auth.User")) return null;
        String[] p = lit.split("\\.");
        String name = p[p.length - 1];
        List<Cx> l = a.classesByName.getOrDefault(name, List.of());
        for (Cx c : l) if (isModel(c) && (p.length < 2 || c.pf.app.equals(p[p.length - 2]) || c.pf.dotted.contains(p[0]))) return c;
        for (Cx c : l) if (isModel(c)) return c;
        return null;
    }

    boolean isBuiltinUserRef(String ref) {
        String r = ref.strip();
        if (r.contains("AUTH_USER_MODEL") || r.contains("get_user_model")) return userModel() == null;
        String lit = PyParser.literal(r);
        if (lit != null) return lit.equals("auth.User") || lit.equals("auth.user");
        return r.equals("User");
    }

    Cx resolveModelRef(Cx ctx, String ref) {
        String r = ref.strip();
        if (r.contains("AUTH_USER_MODEL") || r.contains("get_user_model")) return userModel();
        String lit = PyParser.literal(r);
        if (lit != null) {
            if (lit.equals("self")) return ctx;
            String[] p = lit.split("\\.");
            String name = p[p.length - 1];
            String app = p.length > 1 ? p[p.length - 2] : null;
            List<Cx> cands = a.classesByName.getOrDefault(name, List.of()).stream().filter(DjangoSupport::isModel).toList();
            if (cands.isEmpty()) return null;
            if (app != null) for (Cx c : cands) if (c.pf.app.equals(app) || c.pf.dotted.contains("." + app + ".") || c.pf.dotted.startsWith(app + ".")) return c;
            for (Cx c : cands) if (c.pf.app.equals(ctx.pf.app)) return c;
            return cands.size() == 1 ? cands.get(0) : null;
        }
        if (r.equals("self")) return ctx;
        Cx c = a.classByExpr(ctx.pf, r, true);
        return isModel(c) ? c : null;
    }

    /** The model class a view works with (from {@code model}, {@code queryset} or the serializer's Meta.model). */
    Cx modelOfView(Cx view) {
        if (view == null) return null;
        String m = a.attr(view, "py.model");
        if (m != null) {
            Cx c = a.classByExpr(view.pf, m, true);
            if (isModel(c)) return c;
        }
        String q = a.attr(view, "py.queryset");
        if (q != null) {
            Matcher mm = Pattern.compile("^([A-Za-z_][\\w.]*)\\.(?:objects|_default_manager)").matcher(q.strip());
            if (mm.find()) {
                Cx c = a.classByExpr(view.pf, mm.group(1), true);
                if (isModel(c)) return c;
            }
        }
        Cx ser = serializerOf(view);
        if (ser != null) {
            String sm = a.attr(ser, "meta.model");
            if (sm != null) {
                Cx c = a.classByExpr(ser.pf, sm, true);
                if (isModel(c)) return c;
            }
        }
        String fc = a.attr(view, "py.form_class");
        if (fc != null) {
            Cx form = a.classByExpr(view.pf, fc, true);
            String fm = form == null ? null : a.attr(form, "meta.model");
            if (fm != null) {
                Cx c = a.classByExpr(form.pf, fm, true);
                if (isModel(c)) return c;
            }
        }
        return null;
    }

    private Cx serializerOf(Cx view) {
        String s = a.attr(view, "py.serializer_class");
        return s == null ? null : a.classByExpr(view.pf, s, true);
    }

    // ═════════════════════════ URL configuration → endpoints ═════════════════════════

    private record UrlEl(String kind, String route, boolean regex, String target, int line) {}

    private record Reg(String prefix, String viewset, PF file, int line) {}

    private final List<EndpointInfo> endpoints = new ArrayList<>();
    private final Set<String> endpointIds = new HashSet<>();
    private final Map<String, Acc> accessByEndpoint = new HashMap<>();
    private final Map<String, List<Reg>> routerRegs = new HashMap<>();
    private final Map<PF, List<UrlEl>> urlCache = new HashMap<>();
    private final Set<PF> visited = new HashSet<>();
    private int csrfExempt;

    List<EndpointInfo> endpoints() {
        collectRouters();
        List<PF> urlFiles = a.files.values().stream().filter(p -> p.stem().equals("urls") && !p.test).toList();
        Set<PF> included = new HashSet<>();
        for (PF f : urlFiles) {
            for (UrlEl el : urlElements(f)) {
                if (el.kind().equals("MODULE_INCLUDE")) {
                    PF t = a.moduleByDotted(el.target());
                    if (t != null) included.add(t);
                }
            }
        }
        List<PF> roots = new ArrayList<>();
        String rootConf = settingLiteral("ROOT_URLCONF");
        PF rootPf = rootConf == null ? null : a.moduleByDotted(rootConf);
        if (rootPf != null) roots.add(rootPf);
        else for (PF f : urlFiles) if (!included.contains(f)) roots.add(f);
        for (PF r : roots) walk(r, "", new HashSet<>());
        for (PF f : urlFiles) if (!visited.contains(f)) walk(f, "", new HashSet<>());
        for (Cx c : a.allCx) {
            for (PM m : c.methods) for (Ann an : m.anns) if (an.name().equals("csrf_exempt")) csrfExempt++;
        }
        return endpoints;
    }

    private void collectRouters() {
        Pattern reg = Pattern.compile("^([A-Za-z_][\\w.]*)\\.register\\((.*)\\)$", Pattern.DOTALL);
        for (PF pf : a.files.values()) {
            if (pf.test) continue;
            for (PyParser.Stmt st : pf.mod.top) {
                Matcher m = reg.matcher(st.text());
                if (!m.matches()) continue;
                List<String> pos = new ArrayList<>();
                Map<String, String> kw = new LinkedHashMap<>();
                PyParser.args(m.group(2), pos, kw);
                if (pos.size() < 2) continue;
                String prefix = PyParser.literal(pos.get(0));
                if (prefix == null) continue;
                routerRegs.computeIfAbsent(pf.path + "|" + m.group(1), k -> new ArrayList<>()).add(new Reg(prefix, pos.get(1), pf, st.line()));
            }
        }
    }

    private List<UrlEl> urlElements(PF f) {
        return urlCache.computeIfAbsent(f, k -> {
            List<UrlEl> out = new ArrayList<>();
            for (PyParser.Stmt st : f.mod.top) {
                String t = st.text();
                Matcher m = Pattern.compile("^urlpatterns\\s*(?:\\+?=)\\s*(.*)$", Pattern.DOTALL).matcher(t);
                if (m.matches()) { urlExpr(m.group(1), st.line(), out); continue; }
                Matcher ap = Pattern.compile("^urlpatterns\\.(?:append|extend)\\((.*)\\)$", Pattern.DOTALL).matcher(t);
                if (ap.matches()) urlExpr(ap.group(1), st.line(), out);
            }
            return out;
        });
    }

    private void urlExpr(String expr, int line, List<UrlEl> out) {
        // elements of every top-level [...] group
        int i = 0;
        String masked = PyParser.maskStrings(expr);
        while (i < expr.length()) {
            char c = masked.charAt(i);
            if (c == '[' || c == '(') {
                int close = PyParser.matchParen(expr, i);
                if (close < 0) break;
                if (c == '[') {
                    for (String el : PyParser.splitTop(expr.substring(i + 1, close))) {
                        int off = expr.indexOf(el, i);
                        int ln = line + (int) expr.substring(0, Math.max(0, off)).chars().filter(x -> x == '\n').count();
                        urlElement(el, ln, out);
                    }
                } else {
                    // wrapper such as format_suffix_patterns([...]) — look inside
                    urlExpr(expr.substring(i + 1, close), line + (int) expr.substring(0, i).chars().filter(x -> x == '\n').count(), out);
                }
                i = close + 1;
            } else {
                i++;
            }
        }
        Matcher m = Pattern.compile("([A-Za-z_][\\w.]*)\\.urls\\b").matcher(masked);
        while (m.find()) {
            if (insideBrackets(masked, m.start())) continue;
            out.add(new UrlEl("ROUTER_INCLUDE", "", false, m.group(1), line));
        }
    }

    private static boolean insideBrackets(String masked, int pos) {
        int depth = 0;
        for (int i = 0; i < pos; i++) {
            char c = masked.charAt(i);
            if (c == '[' || c == '(') depth++;
            else if (c == ']' || c == ')') depth--;
        }
        return depth > 0;
    }

    private void urlElement(String el, int line, List<UrlEl> out) {
        String[] call = PyParser.call(el);
        if (call == null) return;
        String fn = PythonAnalyzer.lastSeg(call[0]);
        if (!Set.of("path", "re_path", "url").contains(fn)) return;
        List<String> pos = new ArrayList<>();
        Map<String, String> kw = new LinkedHashMap<>();
        PyParser.args(call[1], pos, kw);
        if (pos.size() < 2) return;
        String route = PyParser.literal(pos.get(0));
        if (route == null) route = pos.get(0).strip();
        boolean regex = !fn.equals("path");
        String target = pos.get(1).strip();
        String[] inc = PyParser.call(target);
        if (inc != null && PythonAnalyzer.lastSeg(inc[0]).equals("include")) {
            List<String> ip = new ArrayList<>();
            PyParser.args(inc[1], ip, new LinkedHashMap<>());
            if (ip.isEmpty()) return;
            String first = ip.get(0).strip();
            if (first.startsWith("(")) {
                List<String> tp = PyParser.splitTop(first.substring(1, Math.max(1, first.length() - 1)));
                first = tp.isEmpty() ? first : tp.get(0);
            }
            String lit = PyParser.literal(first);
            if (lit != null) out.add(new UrlEl("MODULE_INCLUDE", route, regex, lit, line));
            else if (first.endsWith(".urls")) out.add(new UrlEl("ROUTER_INCLUDE", route, regex, first.substring(0, first.length() - 5), line));
            else if (first.matches("[A-Za-z_][\\w.]*")) out.add(new UrlEl("MODULE_INCLUDE", route, regex, first, line));
            return;
        }
        if (target.startsWith("admin.site") || target.startsWith("lambda") || target.startsWith("static(")) return;
        out.add(new UrlEl("VIEW", route, regex, target, line));
    }

    private void walk(PF f, String prefix, Set<String> stack) {
        if (!stack.add(f.path)) return;
        visited.add(f);
        for (UrlEl el : urlElements(f)) {
            switch (el.kind()) {
                case "MODULE_INCLUDE" -> {
                    PF t = a.moduleByDotted(el.target());
                    List<EndpointParam> ignored = new ArrayList<>();
                    String p = join(prefix, normRoute(el.route(), el.regex(), ignored));
                    if (t != null) walk(t, p, stack);
                    else if (!el.target().startsWith("django.") && !el.target().startsWith("rest_framework")) {
                        notes.add("URLs under " + p + " come from the external package " + el.target() + ".");
                    } else if (el.target().startsWith("django.contrib.auth")) {
                        notes.add("Django's built-in login/logout/password views are mounted under " + p + ".");
                    }
                }
                case "ROUTER_INCLUDE" -> {
                    List<EndpointParam> ignored = new ArrayList<>();
                    routerEndpoints(f, el.target(), join(prefix, normRoute(el.route(), el.regex(), ignored)));
                }
                default -> {
                    List<EndpointParam> params = new ArrayList<>();
                    String path = join(prefix, normRoute(el.route(), el.regex(), params));
                    // parameters declared by earlier include prefixes are rare; keep path params of this route
                    viewEndpoints(f, path, params, el);
                }
            }
        }
        stack.remove(f.path);
    }

    static String join(String prefix, String route) {
        String p = (prefix == null ? "" : prefix) + "/" + (route == null ? "" : route);
        p = p.replaceAll("/{2,}", "/");
        if (!p.startsWith("/")) p = "/" + p;
        return p;
    }

    static String normRoute(String route, boolean regex, List<EndpointParam> params) {
        if (!regex) {
            Matcher m = Pattern.compile("<(?:(\\w+):)?(\\w+)>").matcher(route);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String conv = m.group(1) == null ? "str" : m.group(1);
                params.add(new EndpointParam(m.group(2), conv.equals("slug") ? "str" : conv, "path", true));
                m.appendReplacement(sb, Matcher.quoteReplacement("{" + m.group(2) + "}"));
            }
            m.appendTail(sb);
            return sb.toString();
        }
        String r = route.replaceFirst("^\\^", "").replaceFirst("\\$$", "").replace("\\Z", "");
        StringBuilder out = new StringBuilder();
        int i = 0;
        int unnamed = 1;
        while (i < r.length()) {
            char c = r.charAt(i);
            if (c == '(') {
                int close = PyParser.matchParen(r, i);
                if (close < 0) { out.append(r.substring(i)); break; }
                String inner = r.substring(i + 1, close);
                Matcher nm = Pattern.compile("^\\?P<(\\w+)>(.*)$", Pattern.DOTALL).matcher(inner);
                if (nm.matches()) {
                    params.add(new EndpointParam(nm.group(1), nm.group(2).contains("\\d") || nm.group(2).contains("0-9") ? "int" : "str", "path", true));
                    out.append('{').append(nm.group(1)).append('}');
                } else if (!inner.startsWith("?")) {
                    String n = "param" + unnamed++;
                    params.add(new EndpointParam(n, inner.contains("\\d") ? "int" : "str", "path", true));
                    out.append('{').append(n).append('}');
                }
                i = close + 1;
                if (i < r.length() && (r.charAt(i) == '?' || r.charAt(i) == '+' || r.charAt(i) == '*')) i++;
                continue;
            }
            if (c == '\\' && i + 1 < r.length()) { out.append(r.charAt(i + 1)); i += 2; continue; }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    // ───────────────────────── routers (DRF) ─────────────────────────

    private void routerEndpoints(PF f, String routerExpr, String prefix) {
        String var = routerExpr.strip();
        List<Reg> regs = routerRegs.get(f.path + "|" + var);
        if (regs == null) {
            // imported router (from .routers import router) or module.router
            String head = var.contains(".") ? var.substring(0, var.indexOf('.')) : var;
            PyParser.Imp imp = f.bindings.get(head);
            if (imp != null) {
                PF target = var.contains(".") ? a.moduleOfAlias(f, head) : a.moduleByDotted(a.importBase(f, imp));
                String name = var.contains(".") ? var.substring(var.lastIndexOf('.') + 1) : imp.name();
                if (target != null && name != null) regs = routerRegs.get(target.path + "|" + name);
            }
        }
        if (regs == null) return;
        for (Reg r : regs) viewSetRoutes(r, prefix);
    }

    private static final List<String> LIST_ACTIONS = List.of("list", "create");
    private static final List<String> DETAIL_ACTIONS = List.of("retrieve", "update", "partial_update", "destroy");

    private void viewSetRoutes(Reg r, String prefix) {
        Cx vs = a.classByExpr(r.file(), r.viewset(), true);
        if (vs == null) return;
        Set<String> bases = a.allBases(vs);
        Set<String> actions = new LinkedHashSet<>();
        if (bases.contains("ModelViewSet")) actions.addAll(List.of("list", "create", "retrieve", "update", "partial_update", "destroy"));
        if (bases.contains("ReadOnlyModelViewSet")) actions.addAll(List.of("list", "retrieve"));
        if (bases.contains("ListModelMixin")) actions.add("list");
        if (bases.contains("CreateModelMixin")) actions.add("create");
        if (bases.contains("RetrieveModelMixin")) actions.add("retrieve");
        if (bases.contains("UpdateModelMixin")) { actions.add("update"); actions.add("partial_update"); }
        if (bases.contains("DestroyModelMixin")) actions.add("destroy");
        for (String act : LIST_ACTIONS) if (a.findMethod(vs, act) != null) actions.add(act);
        for (String act : DETAIL_ACTIONS) if (a.findMethod(vs, act) != null) actions.add(act);
        String lookup = "pk";
        String lf = a.attr(vs, "py.lookup_field");
        if (lf != null && PyParser.literal(lf) != null) lookup = PyParser.literal(lf);
        String base = join(prefix, r.prefix().replaceFirst("^\\^", "").replaceFirst("\\$$", ""));
        String listPath = base.endsWith("/") ? base : base + "/";
        String detailPath = listPath + "{" + lookup + "}/";
        List<EndpointParam> detailParams = List.of(new EndpointParam(lookup, "str", "path", true));
        for (String act : actions) {
            boolean detail = DETAIL_ACTIONS.contains(act);
            String verb = switch (act) {
                case "list", "retrieve" -> "GET";
                case "create" -> "POST";
                case "update" -> "PUT";
                case "partial_update" -> "PATCH";
                default -> "DELETE";
            };
            PM h = handler(vs, act);
            addEndpoint(vs, h, verb, detail ? detailPath : listPath, detail ? detailParams : List.of(), act);
        }
        // @action extras
        Set<Cx> chain = new LinkedHashSet<>();
        collectChain(vs, chain);
        for (Cx c : chain) {
            for (PM m : c.methods) {
                for (Ann an : m.anns) {
                    if (!an.name().equals("action")) continue;
                    boolean detail = "True".equals(an.arg("detail"));
                    List<String> verbs = an.arg("methods") == null ? List.of("get") : PyParser.strings(an.arg("methods"));
                    String sub = an.arg("url_path") != null ? PyParser.literal(an.arg("url_path")) : m.name;
                    String p = (detail ? detailPath : listPath) + sub + "/";
                    for (String v : verbs) addEndpoint(vs, m, v.toUpperCase(Locale.ROOT), p, detail ? detailParams : List.of(), m.name);
                }
            }
        }
    }

    private void collectChain(Cx c, Set<Cx> out) {
        if (c == null || !out.add(c)) return;
        for (Cx b : c.bases) collectChain(b, out);
    }

    // ───────────────────────── views ─────────────────────────

    private void viewEndpoints(PF urlFile, String path, List<EndpointParam> params, UrlEl el) {
        String target = el.target();
        Map<String, String> methodMap = null;
        Map<String, String> asViewKw = new LinkedHashMap<>();
        int av = target.indexOf(".as_view(");
        String ref = target;
        if (av >= 0) {
            ref = target.substring(0, av);
            int open = target.indexOf('(', av);
            int close = PyParser.matchParen(target, open);
            if (close > open) {
                List<String> pos = new ArrayList<>();
                PyParser.args(target.substring(open + 1, close), pos, asViewKw);
                if (!pos.isEmpty() && pos.get(0).startsWith("{")) {
                    methodMap = new LinkedHashMap<>();
                    Matcher m = Pattern.compile("['\"](\\w+)['\"]\\s*:\\s*['\"](\\w+)['\"]").matcher(pos.get(0));
                    while (m.find()) methodMap.put(m.group(1), m.group(2));
                }
            }
        }
        Cx cx = a.classByExpr(urlFile, ref, true);
        if (cx != null && !cx.moduleClass) { classEndpoints(cx, path, params, methodMap, asViewKw); return; }
        PM fn = a.functionByExpr(urlFile, ref);
        if (fn != null) { functionEndpoints(fn, path, params); return; }
        // library view (TemplateView, LoginView, RedirectView …) or something we cannot resolve
        String simple = PythonAnalyzer.lastSeg(ref);
        if (!simple.matches("[A-Z]\\w*")) return;
        Cx host = urlFile.moduleCx;
        if (host == null) return;
        List<String> verbs = libraryVerbs(Set.of(simple));
        if (verbs.isEmpty()) verbs = List.of("GET");
        for (String v : verbs) {
            PM h = synthetic(host, simple + (verbs.size() > 1 ? "_" + v.toLowerCase(Locale.ROOT) : ""),
                    "Handled by the framework's built-in " + simple + ".", null, "", asViewKw.get("template_name"));
            addEndpoint(host, h, v, path, params, v.toLowerCase(Locale.ROOT));
        }
    }

    private static List<String> libraryVerbs(Set<String> bases) {
        LinkedHashSet<String> v = new LinkedHashSet<>();
        for (String b : bases) {
            switch (b) {
                case "ListView", "DetailView", "TemplateView", "RedirectView", "ListAPIView", "RetrieveAPIView", "LogoutView" -> v.add("GET");
                case "CreateView", "UpdateView", "FormView", "DeleteView", "LoginView", "PasswordChangeView" -> { v.add("GET"); v.add("POST"); }
                case "CreateAPIView" -> v.add("POST");
                case "UpdateAPIView" -> { v.add("PUT"); v.add("PATCH"); }
                case "DestroyAPIView" -> v.add("DELETE");
                case "ListCreateAPIView" -> { v.add("GET"); v.add("POST"); }
                case "RetrieveUpdateAPIView" -> { v.add("GET"); v.add("PUT"); v.add("PATCH"); }
                case "RetrieveDestroyAPIView" -> { v.add("GET"); v.add("DELETE"); }
                case "RetrieveUpdateDestroyAPIView" -> { v.add("GET"); v.add("PUT"); v.add("PATCH"); v.add("DELETE"); }
                default -> { }
            }
        }
        return new ArrayList<>(v);
    }

    private void classEndpoints(Cx cx, String path, List<EndpointParam> params, Map<String, String> methodMap, Map<String, String> kw) {
        Map<String, PM> handlers = new LinkedHashMap<>();
        if (methodMap != null) {
            for (Map.Entry<String, String> e : methodMap.entrySet()) handlers.put(e.getKey().toUpperCase(Locale.ROOT), handler(cx, e.getValue()));
        } else {
            for (String v : List.of("get", "post", "put", "patch", "delete")) {
                PM m = a.findMethod(cx, v);
                if (m != null) handlers.put(v.toUpperCase(Locale.ROOT), m);
            }
            for (String v : libraryVerbs(a.allBases(cx))) {
                if (!handlers.containsKey(v)) handlers.put(v, handler(cx, v.toLowerCase(Locale.ROOT)));
            }
        }
        for (Map.Entry<String, PM> e : handlers.entrySet()) addEndpoint(cx, e.getValue(), e.getKey(), path, params, e.getValue().name);
    }

    private void functionEndpoints(PM fn, String path, List<EndpointParam> params) {
        Set<String> verbs = new LinkedHashSet<>();
        String text = bodyText(fn);
        for (Ann an : fn.anns) {
            switch (an.name()) {
                case "require_http_methods", "api_view", "require_http_method" -> {
                    if (an.arg("value") != null) PyParser.strings(an.arg("value")).forEach(s -> verbs.add(s.toUpperCase(Locale.ROOT)));
                    else if (an.name().equals("api_view")) verbs.add("GET");
                }
                case "require_GET", "require_safe" -> verbs.add("GET");
                case "require_POST" -> verbs.add("POST");
                default -> { }
            }
        }
        if (verbs.isEmpty()) {
            Matcher m = Pattern.compile("request\\.method\\s*(?:==|!=|in|not in)\\s*[\\[(]?\\s*['\"](GET|POST|PUT|PATCH|DELETE)['\"](?:\\s*,\\s*['\"](GET|POST|PUT|PATCH|DELETE)['\"])*").matcher(text);
            while (m.find()) {
                Matcher s = Pattern.compile("['\"](GET|POST|PUT|PATCH|DELETE)['\"]").matcher(m.group());
                while (s.find()) verbs.add(s.group(1));
            }
            if (!verbs.isEmpty() && !verbs.contains("GET") && text.contains("render(")) verbs.add("GET");
        }
        if (verbs.isEmpty()) {
            verbs.add("GET");
            if (text.contains("request.POST") || text.contains("request.FILES")) verbs.add("POST");
        }
        for (String v : verbs) addEndpoint(fn.owner, fn, v, path, params, fn.name);
    }

    private PM handler(Cx cx, String action) {
        PM m = a.findMethod(cx, action);
        if (m != null) return m;
        String bn = cx.baseTexts.isEmpty() ? "the framework" : PythonAnalyzer.lastSeg(cx.baseTexts.get(0));
        return synthetic(cx, action, "Built-in " + bn + "." + action + "() — inherited, no code of its own.", modelOfView(cx), action, a.attr(cx, "py.template_name"));
    }

    private PM synthetic(Cx cx, String name, String doc, Cx model, String action, String template) {
        Map<String, PM> idx = a.methodIndex.computeIfAbsent(cx, k -> new LinkedHashMap<>());
        PM existing = idx.get(name);
        if (existing != null && existing.synthetic) return existing;
        PM m = new PM();
        m.synthetic = true;
        m.owner = cx;
        m.name = name;
        m.id = cx.id + "#" + name + "()";
        m.doc = doc;
        m.line = cx.line;
        m.endLine = cx.line;
        m.loc = 0;
        m.anns.add(new Ann("inherited", Map.of()));
        if (!action.isEmpty()) {
            String lower = action.toLowerCase(Locale.ROOT);
            Cx ser = serializerOf(cx);
            boolean write = lower.matches("create|post|update|put|patch|partial_update");
            if (ser != null && write) m.calls.add(new Call(ser.id, ser.name, "is_valid", cx.line, null, true));
            // overridden hooks of DRF's generic views run inside the built-in handler
            String hook = lower.equals("create") ? "perform_create" : lower.matches("update|partial_update") ? "perform_update"
                    : lower.equals("destroy") ? "perform_destroy" : null;
            PM hookMethod = hook == null ? null : a.findMethod(cx, hook);
            PM queryset = write && !lower.equals("create") || lower.matches("list|retrieve|destroy|get") ? a.findMethod(cx, "get_queryset") : null;
            if (hookMethod != null) m.calls.add(new Call(cx.id, cx.name, hook, cx.line, hookMethod.id, false));
            else if (queryset != null) m.calls.add(new Call(cx.id, cx.name, "get_queryset", cx.line, queryset.id, false));
            else if (model != null) {
                String op = lower.matches("create|post") ? "create" : lower.matches("update|put|patch|partial_update") ? "update"
                        : lower.matches("destroy|delete") ? "delete" : lower.matches("list") || (lower.equals("get") && a.allBases(cx).stream().anyMatch(b -> b.contains("List"))) ? "all" : "get";
                m.calls.add(new Call(model.id, model.name, op, cx.line, null, true));
            }
        }
        if (template != null) m.doc = m.doc + " Renders " + template + ".";
        cx.methods.add(m);
        idx.put(name, m);
        return m;
    }

    // ───────────────────────── endpoint assembly ─────────────────────────

    private void addEndpoint(Cx ctrl, PM h, String verb, String path, List<EndpointParam> pathParams, String action) {
        String id = verb + " " + path + " @" + ctrl.id + "#" + h.name;
        if (!endpointIds.add(id)) return;
        List<EndpointParam> ps = new ArrayList<>(pathParams);
        String text = bodyText(h);
        requestParams(ctrl, h, verb, text, ps);
        Acc acc = access(ctrl, h, verb);
        String returns;
        boolean view = false;
        Cx ser = serializerOf(ctrl);
        Matcher tpl = Pattern.compile("render\\(\\s*\\w+\\s*,\\s*['\"]([^'\"]+)['\"]").matcher(text);
        String template = a.attr(ctrl, "py.template_name");
        boolean htmlBase = a.allBases(ctrl).stream().anyMatch(b -> Set.of("ListView", "DetailView", "TemplateView", "FormView", "CreateView", "UpdateView", "DeleteView").contains(b));
        if (tpl.find()) { returns = "HTML page (" + tpl.group(1) + ")"; view = true; }
        else if (h.synthetic && htmlBase && verb.equals("GET")) {
            returns = "HTML page" + (template != null && PyParser.literal(template) != null ? " (" + PyParser.literal(template) + ")" : "");
            view = true;
        } else if (text.contains("render_template(")) { returns = "HTML page"; view = true; }
        else if (text.contains("JsonResponse(") || text.contains("jsonify(")) returns = "JSON";
        else if (ser != null) returns = action.equals("list") ? "list of " + ser.name : ser.name;
        else if (text.contains("Response(")) returns = "JSON";
        else if (text.contains("redirect(") || text.contains("HttpResponseRedirect(")) returns = "redirect";
        else if (h.synthetic && htmlBase) returns = "redirect";
        else if (h.fn != null && h.fn.returns != null) returns = h.fn.returns;
        else returns = "HttpResponse";
        EndpointInfo e = new EndpointInfo(id, verb, path, path, ctrl.id, h.id, ctrl.pf.path, h.line, ps, returns, acc.access(), acc.note(),
                null, view);
        endpoints.add(e);
        accessByEndpoint.put(id, acc);
        h.endpointId = id;
    }

    private String bodyText(PM m) {
        if (m.fn == null) return "";
        StringBuilder sb = new StringBuilder();
        for (PyParser.Stmt s : m.fn.body) sb.append(s.text()).append('\n');
        return sb.toString();
    }

    private void requestParams(Cx ctrl, PM h, String verb, String text, List<EndpointParam> ps) {
        Set<String> seen = new HashSet<>();
        for (EndpointParam p : ps) seen.add(p.source() + ":" + p.name());
        String[][] patterns = {
                {"query", "request\\.(?:GET|query_params|args)\\.get\\(\\s*['\"]([\\w-]+)['\"]"},
                {"query", "request\\.(?:GET|query_params|args)\\[\\s*['\"]([\\w-]+)['\"]\\s*]"},
                {"form", "request\\.(?:POST|form)\\.get\\(\\s*['\"]([\\w-]+)['\"]"},
                {"form", "request\\.(?:POST|form)\\[\\s*['\"]([\\w-]+)['\"]\\s*]"},
                {"body", "request\\.(?:data|json)\\.get\\(\\s*['\"]([\\w-]+)['\"]"},
                {"body", "request\\.(?:data|json)\\[\\s*['\"]([\\w-]+)['\"]\\s*]"},
                {"header", "request\\.headers\\.get\\(\\s*['\"]([\\w-]+)['\"]"},
        };
        for (String[] pt : patterns) {
            Matcher m = Pattern.compile(pt[1]).matcher(text);
            while (m.find()) if (seen.add(pt[0] + ":" + m.group(1))) ps.add(new EndpointParam(m.group(1), "string", pt[0], false));
        }
        if (text.contains("request.FILES") && seen.add("part:file")) ps.add(new EndpointParam("file", "file", "part", false));
        if ((text.contains("request.body") || text.matches("(?s).*request\\.data\\b(?!\\s*[.\\[]).*")) && seen.add("body:body")) {
            ps.add(new EndpointParam("body", "json", "body", true));
        }
        // serializer / form classes used with the request data
        Matcher sm = Pattern.compile("\\b([A-Z]\\w*(?:Serializer|Form))\\s*\\(([^)]*)").matcher(text);
        while (sm.find()) {
            if (!(sm.group(2).contains("request.") || sm.group(2).contains("data="))) continue;
            Cx ser = a.classByExpr(ctrl.pf, sm.group(1), true);
            addFields(ser, sm.group(1).endsWith("Form") ? "form" : "body", ps, seen);
        }
        if (h.synthetic && !h.calls.isEmpty() && Set.of("POST", "PUT", "PATCH").contains(verb)) {
            Cx ser = serializerOf(ctrl);
            if (ser != null) addFields(ser, "body", ps, seen);
            else {
                String fc = a.attr(ctrl, "py.form_class");
                Cx form = fc == null ? null : a.classByExpr(ctrl.pf, fc, true);
                if (form != null) addFields(form, "form", ps, seen);
            }
        }
        if (h.synthetic && h.calls.isEmpty() && Set.of("POST", "PUT", "PATCH").contains(verb)) {
            String fc = a.attr(ctrl, "py.form_class");
            Cx form = fc == null ? null : a.classByExpr(ctrl.pf, fc, true);
            if (form != null) addFields(form, "form", ps, seen);
        }
    }

    private void addFields(Cx ser, String source, List<EndpointParam> ps, Set<String> seen) {
        if (ser == null) return;
        for (String[] f : serializerFields(ser)) {
            if (f[0].equals("id") || f[1].equals("integer") && f[0].equals("pk")) continue; // generated by the database, not sent by the client
            if (seen.add(source + ":" + f[0])) ps.add(new EndpointParam(f[0], f[1], source, true));
        }
    }

    /** [name, type] of the fields a serializer / form exposes. */
    private List<String[]> serializerFields(Cx ser) {
        List<String[]> out = new ArrayList<>();
        String meta = a.attr(ser, "meta.fields");
        Cx model = null;
        String mm = a.attr(ser, "meta.model");
        if (mm != null) model = a.classByExpr(ser.pf, mm, true);
        Map<String, String> modelFieldTypes = new LinkedHashMap<>();
        if (model != null) for (FieldInfo f : model.fields) {
            if (!f.isStatic() && !f.annotations().isEmpty()) modelFieldTypes.put(f.name(), simpleFieldType(f));
        }
        List<String> names = new ArrayList<>();
        if (meta != null && !meta.contains("__all__")) names.addAll(PyParser.strings(meta));
        else if (meta != null) names.addAll(modelFieldTypes.keySet());
        for (FieldInfo f : ser.fields) if (!f.isStatic() && !f.annotations().isEmpty() && !names.contains(f.name())) names.add(f.name());
        for (String n : names) {
            String t = modelFieldTypes.get(n);
            FieldInfo own = a.findField(ser, n);
            if (own != null && !own.annotations().isEmpty()) t = simpleFieldType(own);
            out.add(new String[]{n, t == null ? "string" : t});
        }
        return out;
    }

    private static String simpleFieldType(FieldInfo f) {
        String t = f.annotations().isEmpty() ? f.type() : f.annotations().get(0).name();
        return switch (t) {
            case "CharField", "TextField", "EmailField", "SlugField", "URLField", "RegexField" -> "string";
            case "IntegerField", "BigIntegerField", "SmallIntegerField", "PositiveIntegerField", "AutoField", "BigAutoField" -> "integer";
            case "FloatField", "DecimalField" -> "number";
            case "BooleanField", "NullBooleanField" -> "boolean";
            case "DateField" -> "date";
            case "DateTimeField" -> "datetime";
            case "UUIDField" -> "uuid";
            case "ForeignKey", "OneToOneField", "PrimaryKeyRelatedField" -> "id";
            case "ManyToManyField" -> "list of ids";
            case "JSONField" -> "json";
            case "FileField", "ImageField" -> "file";
            default -> t.endsWith("Field") ? t.substring(0, t.length() - 5).toLowerCase(Locale.ROOT) : "string";
        };
    }

    // ───────────────────────── access ─────────────────────────

    /** Access level of an endpoint; {@code explicit} is false when it only comes from a project-wide default. */
    record Acc(String access, String note, boolean explicit) {
        Acc(String access, String note) { this(access, note, true); }
    }

    private List<String> drfDefaultPermissions() {
        Sett rf = setting("REST_FRAMEWORK");
        if (rf == null) return List.of();
        Matcher m = Pattern.compile("['\"]DEFAULT_PERMISSION_CLASSES['\"]\\s*:\\s*[\\[(]([^\\])]*)[\\])]").matcher(rf.value);
        if (!m.find()) return List.of();
        return PyParser.strings(m.group(1)).stream().map(PythonAnalyzer::lastSeg).toList();
    }

    private Acc access(Cx cx, PM h, String verb) {
        List<Ann> decos = new ArrayList<>(h.anns);
        decos.addAll(cx.anns);
        Set<String> bases = a.allBases(cx);
        String note = null;
        for (Ann d : decos) {
            switch (d.name()) {
                case "login_required" -> { return new Acc("AUTHENTICATED", "@login_required — redirects anonymous users to the login page"); }
                case "staff_member_required" -> { return new Acc("ROLE:staff", "@staff_member_required"); }
                case "superuser_required" -> { return new Acc("ROLE:superuser", "@superuser_required"); }
                case "permission_required" -> {
                    String p = d.arg("value") == null ? "permission" : PyParser.strings(d.arg("value")).stream().findFirst().orElse("permission");
                    return new Acc("AUTHORITY:" + p, "@permission_required(" + p + ")");
                }
                case "user_passes_test" -> { return new Acc("CUSTOM:" + (d.arg("value") == null ? "user_passes_test" : d.arg("value")), "@user_passes_test"); }
                case "method_decorator" -> {
                    String v = d.arg("value") == null ? "" : d.arg("value");
                    if (v.contains("login_required")) return new Acc("AUTHENTICATED", "@method_decorator(login_required)");
                    if (v.contains("permission_required")) return new Acc("AUTHORITY:permission", "@method_decorator(permission_required)");
                }
                case "csrf_exempt" -> note = "@csrf_exempt — CSRF protection is switched off for this view";
                case "permission_classes" -> {
                    if (d.arg("value") != null) {
                        List<String> names = identifiers(d.arg("value"));
                        return mapPermissions(names, verb, "@permission_classes");
                    }
                }
                default -> { }
            }
        }
        if (bases.contains("LoginRequiredMixin")) return new Acc("AUTHENTICATED", "LoginRequiredMixin");
        if (bases.contains("PermissionRequiredMixin")) {
            String pr = a.attr(cx, "py.permission_required");
            String p = pr == null ? "permission" : PyParser.strings(pr).stream().findFirst().orElse("permission");
            return new Acc("AUTHORITY:" + p, "PermissionRequiredMixin(" + p + ")");
        }
        if (bases.contains("UserPassesTestMixin")) return new Acc("CUSTOM:UserPassesTestMixin", "UserPassesTestMixin");
        String pc = a.attr(cx, "py.permission_classes");
        if (pc != null) return mapPermissions(identifiers(pc), verb, "permission_classes");
        if (a.findMethod(cx, "get_permissions") != null && h.fn != null) {
            return new Acc("CUSTOM:get_permissions()", "permissions are chosen in get_permissions()");
        }
        boolean drf = bases.stream().anyMatch(b -> b.endsWith("APIView") || b.endsWith("ViewSet") || b.equals("GenericAPIView"))
                || decos.stream().anyMatch(d -> d.name().equals("api_view"));
        if (drf) {
            List<String> def = drfDefaultPermissions();
            if (!def.isEmpty()) {
                Acc d = mapPermissions(def, verb, "REST_FRAMEWORK default");
                return new Acc(d.access(), d.note(), false);
            }
            return new Acc("PUBLIC", note != null ? note : "DRF default permission is AllowAny", false);
        }
        return new Acc("PUBLIC", note, false);
    }

    private static List<String> identifiers(String expr) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("[A-Za-z_][\\w.]*").matcher(PyParser.maskStrings(expr));
        while (m.find()) out.add(PythonAnalyzer.lastSeg(m.group()));
        List<String> strs = PyParser.strings(expr);
        for (String s : strs) out.add(PythonAnalyzer.lastSeg(s));
        return out;
    }

    private Acc mapPermissions(List<String> perms, String verb, String origin) {
        boolean safe = verb.equals("GET") || verb.equals("HEAD") || verb.equals("OPTIONS");
        if (perms.contains("IsAdminUser")) return new Acc("ROLE:admin", origin + " = IsAdminUser");
        if (perms.contains("IsAuthenticated")) {
            List<String> custom = perms.stream().filter(p -> !Set.of("IsAuthenticated", "AllowAny").contains(p)).toList();
            return new Acc("AUTHENTICATED", origin + " = IsAuthenticated" + (custom.isEmpty() ? "" : " + " + String.join(", ", custom)));
        }
        if (perms.contains("IsAuthenticatedOrReadOnly")) {
            return new Acc(safe ? "PUBLIC" : "AUTHENTICATED", origin + " = IsAuthenticatedOrReadOnly (read is public, write needs login)");
        }
        if (perms.contains("DjangoModelPermissions") || perms.contains("DjangoModelPermissionsOrAnonReadOnly") || perms.contains("DjangoObjectPermissions")) {
            return new Acc("AUTHORITY:model permissions", origin + " = " + perms.stream().filter(p -> p.startsWith("Django")).findFirst().orElse("DjangoModelPermissions"));
        }
        if (perms.contains("AllowAny")) return new Acc("PUBLIC", origin + " = AllowAny");
        if (perms.isEmpty()) return new Acc("PUBLIC", origin);
        return new Acc("CUSTOM:" + String.join(",", perms), origin + " = " + String.join(", ", perms));
    }

    // ═════════════════════════ Flask / FastAPI ═════════════════════════

    private static final Set<String> ROUTE_DECOS = Set.of("route", "get", "post", "put", "delete", "patch", "api_route");

    List<EndpointInfo> decoratorRoutes() {
        List<EndpointInfo> out = new ArrayList<>();
        for (PF pf : a.files.values()) {
            if (pf.test) continue;
            Map<String, String> prefixes = new HashMap<>();
            for (PyParser.Stmt st : pf.mod.top) {
                PyParser.Assign as = PyParser.assign(st.text());
                if (as == null || as.value() == null) continue;
                String[] call = PyParser.call(as.value());
                if (call == null) continue;
                String fn = PythonAnalyzer.lastSeg(call[0]);
                if (fn.equals("Blueprint") || fn.equals("APIRouter")) {
                    List<String> pos = new ArrayList<>();
                    Map<String, String> kw = new LinkedHashMap<>();
                    PyParser.args(call[1], pos, kw);
                    String p = PyParser.literal(kw.getOrDefault("url_prefix", kw.get("prefix")));
                    prefixes.put(as.name(), p == null ? "" : p);
                }
            }
            for (Cx c : pf.classes.isEmpty() && pf.moduleCx == null ? List.<Cx>of() : allOf(pf)) {
                for (PM m : c.methods) {
                    if (m.fn == null) continue;
                    for (String d : m.fn.decorators) {
                        Matcher dm = Pattern.compile("^([A-Za-z_]\\w*)\\.(\\w+)\\((.*)\\)$", Pattern.DOTALL).matcher(d.strip());
                        if (!dm.matches() || !ROUTE_DECOS.contains(dm.group(2))) continue;
                        List<String> pos = new ArrayList<>();
                        Map<String, String> kw = new LinkedHashMap<>();
                        PyParser.args(dm.group(3), pos, kw);
                        String lit = PyParser.literal(pos.isEmpty() ? kw.get("path") : pos.get(0));
                        if (lit == null || !lit.startsWith("/")) continue;
                        List<EndpointParam> params = new ArrayList<>();
                        String path = join(prefixes.getOrDefault(dm.group(1), ""), normRoute(lit, false, params));
                        List<String> verbs = new ArrayList<>();
                        if (dm.group(2).equals("route") || dm.group(2).equals("api_route")) {
                            if (kw.get("methods") != null) PyParser.strings(kw.get("methods")).forEach(s -> verbs.add(s.toUpperCase(Locale.ROOT)));
                            if (verbs.isEmpty()) verbs.add("GET");
                        } else verbs.add(dm.group(2).toUpperCase(Locale.ROOT));
                        boolean fastapi = pf.mod.imports.stream().anyMatch(i -> i.module() != null && i.module().startsWith("fastapi"));
                        boolean flask = pf.mod.imports.stream().anyMatch(i -> i.module() != null && i.module().startsWith("flask"));
                        if (fastapi) frameworkSet.add("FastAPI");
                        if (flask) frameworkSet.add("Flask");
                        for (String v : verbs) {
                            EndpointInfo e = routeEndpoint(c, m, v, path, params, fastapi);
                            if (e != null) out.add(e);
                        }
                    }
                }
            }
        }
        return out;
    }

    private List<Cx> allOf(PF pf) {
        List<Cx> l = new ArrayList<>();
        if (pf.moduleCx != null) l.add(pf.moduleCx);
        l.addAll(pf.classes);
        return l;
    }

    private EndpointInfo routeEndpoint(Cx c, PM m, String verb, String path, List<EndpointParam> pathParams, boolean fastapi) {
        String id = verb + " " + path + " @" + c.id + "#" + m.name;
        if (!endpointIds.add(id)) return null;
        List<EndpointParam> ps = new ArrayList<>(pathParams);
        Set<String> pathNames = pathParams.stream().map(EndpointParam::name).collect(Collectors.toSet());
        boolean auth = m.anns.stream().anyMatch(d -> Set.of("login_required", "jwt_required", "auth_required", "token_required", "roles_required", "fresh_jwt_required").contains(d.name()));
        for (PyParser.Param p : m.fn.params) {
            if (p.name().equals("self") || p.name().equals("request") || p.name().equals("response")) continue;
            String def = p.defaultValue() == null ? "" : p.defaultValue();
            if (def.startsWith("Depends(") || def.startsWith("Security(")) {
                if (def.toLowerCase(Locale.ROOT).matches(".*(current_user|get_current|auth|verify|token|security|permission).*")) auth = true;
                continue;
            }
            if (pathNames.contains(p.name())) continue;
            if (!fastapi) continue;
            String ann = p.annotation() == null ? "" : p.annotation();
            Cx model = ann.isEmpty() ? null : a.classByExpr(c.pf, ann.split("[\\[|,\\s]")[0], true);
            boolean body = model != null && "DTO".equals(model.layer) && Set.of("POST", "PUT", "PATCH").contains(verb);
            ps.add(new EndpointParam(p.name(), ann.isEmpty() ? "string" : ann, body ? "body" : "query", p.defaultValue() == null));
        }
        String text = bodyText(m);
        requestParams(c, m, verb, text.replace("request.args", "request.args"), ps);
        String returns = m.fn.returns != null ? m.fn.returns : text.contains("render_template(") ? "HTML page" : "JSON";
        boolean view = text.contains("render_template(");
        Acc acc = auth ? new Acc("AUTHENTICATED", "login / token dependency") : new Acc("PUBLIC", null);
        EndpointInfo e = new EndpointInfo(id, verb, path, path, c.id, m.id, c.pf.path, m.line, ps, returns, acc.access(), acc.note(), null, view);
        accessByEndpoint.put(id, acc);
        m.endpointId = id;
        return e;
    }

    // ═════════════════════════ schema (Django models) ═════════════════════════

    private static final class Def {
        Cx cx;
        String table;
        boolean abstractModel, proxy;
        String pkName = "id", pkType = "bigint";
        final List<Column> cols = new ArrayList<>();
        final List<FieldInfo> fields = new ArrayList<>();
    }

    Schema schema() {
        List<Cx> models = a.allCx.stream().filter(c -> isModel(c) && !c.test).toList();
        if (models.isEmpty()) return null;
        String auto = settingLiteral("DEFAULT_AUTO_FIELD");
        String autoType = auto != null && auto.contains("BigAutoField") ? "bigint" : (setting("DEFAULT_AUTO_FIELD") == null ? "int" : "int");
        for (Cx c : a.allCx) {
            String d = c.attrs.get("py.default_auto_field");
            if (d != null && d.contains("BigAutoField")) autoType = "bigint";
        }
        Map<Cx, Def> defs = new LinkedHashMap<>();
        for (Cx m : models) {
            Def d = new Def();
            d.cx = m;
            d.abstractModel = "True".equals(m.attrs.get("meta.abstract"));
            d.proxy = "True".equals(m.attrs.get("meta.proxy"));
            String dbTable = PyParser.literal(m.attrs.get("meta.db_table"));
            String label = PyParser.literal(m.attrs.get("meta.app_label"));
            d.table = dbTable != null ? dbTable : (label != null ? label : m.pf.app).toLowerCase(Locale.ROOT) + "_" + m.name.toLowerCase(Locale.ROOT);
            defs.put(m, d);
        }
        // effective fields (abstract bases first)
        for (Def d : defs.values()) d.fields.addAll(effectiveFields(d.cx, new HashSet<>()));
        // primary keys
        for (Def d : defs.values()) {
            d.pkType = autoType;
            for (FieldInfo f : d.fields) {
                Ann an = f.annotations().isEmpty() ? null : f.annotations().get(0);
                if (an != null && "True".equals(an.arg("primary_key")) && !RELATION_TYPES.contains(an.name())) {
                    d.pkName = columnNameOf(f);
                    d.pkType = sqlType(an.name(), an);
                }
            }
        }
        Map<String, Def> byTable = new LinkedHashMap<>();
        for (Def d : defs.values()) if (!d.abstractModel && !d.proxy) byTable.put(d.table, d);

        Map<String, List<Column>> columns = new LinkedHashMap<>();
        Map<String, Cx> tableEntity = new LinkedHashMap<>();
        List<Relation> relations = new ArrayList<>();
        Map<String, int[]> tableLine = new HashMap<>();
        boolean needBuiltinUser = false;

        for (Def d : byTable.values()) {
            List<Column> cols = new ArrayList<>();
            boolean hasExplicitPk = d.fields.stream().anyMatch(f -> !f.annotations().isEmpty() && "True".equals(f.annotations().get(0).arg("primary_key")));
            // multi-table inheritance
            Cx parent = concreteParent(d.cx, defs);
            if (parent != null) {
                Def pd = defs.get(parent);
                String col = parent.name.toLowerCase(Locale.ROOT) + "_ptr_id";
                cols.add(new Column(col, pd.pkType, "OneToOneField", false, true, true, false, null, pd.table + "." + pd.pkName, parent.name.toLowerCase(Locale.ROOT) + "_ptr"));
                relations.add(new Relation(d.table, pd.table, col, pd.pkName, "ONE_TO_ONE", "1:1", parent.name.toLowerCase(Locale.ROOT) + "_ptr", null, null));
                d.pkName = col;
                d.pkType = pd.pkType;
            } else if (!hasExplicitPk) {
                cols.add(new Column("id", d.pkType, "AutoField", false, true, true, true, null, null, "id"));
            }
            addAuthColumns(d, cols);
            for (FieldInfo f : d.fields) {
                if (f.annotations().isEmpty()) continue;
                Ann an = f.annotations().get(0);
                String t = an.name();
                if (t.equals("GenericForeignKey") || t.equals("GenericRelation") || an.arg("through") != null && t.equals("ManyToManyField")) continue;
                if (RELATION_TYPES.contains(t)) {
                    if (t.equals("ManyToManyField")) continue; // handled below
                    String ref = an.arg("to") != null ? an.arg("to") : an.arg("value");
                    Cx target = f.resolvedClass() == null ? null : a.classesById.get(f.resolvedClass());
                    String tTable;
                    String tPk;
                    String tPkType;
                    if (target != null && defs.containsKey(target)) {
                        Def td = defs.get(target);
                        tTable = td.table; tPk = td.pkName; tPkType = td.pkType;
                    } else if (ref != null && isBuiltinUserRef(ref)) {
                        tTable = "auth_user"; tPk = "id"; tPkType = "int"; needBuiltinUser = true;
                    } else continue;
                    String col = an.arg("db_column") != null ? PyParser.literal(an.arg("db_column")) : f.name() + "_id";
                    boolean o2o = t.equals("OneToOneField");
                    boolean nullable = "True".equals(an.arg("null"));
                    boolean pk = "True".equals(an.arg("primary_key"));
                    cols.add(new Column(col, tPkType, t, nullable && !pk, o2o || pk || "True".equals(an.arg("unique")), pk, false, null,
                            tTable + "." + tPk, f.name()));
                    relations.add(new Relation(d.table, tTable, col, tPk, o2o ? "ONE_TO_ONE" : "MANY_TO_ONE", o2o ? "1:1" : "N:1", f.name(), null, null));
                    continue;
                }
                String col = an.arg("db_column") != null && PyParser.literal(an.arg("db_column")) != null ? PyParser.literal(an.arg("db_column")) : f.name();
                boolean pk = "True".equals(an.arg("primary_key"));
                boolean gen = t.equals("AutoField") || t.equals("BigAutoField") || t.equals("SmallAutoField");
                Integer len = intOf(an.arg("max_length"));
                if (len == null && t.equals("EmailField")) len = 254;
                if (len == null && t.equals("SlugField")) len = 50;
                if (len == null && t.equals("URLField")) len = 200;
                if (len == null && (t.equals("FileField") || t.equals("ImageField"))) len = 100;
                cols.add(new Column(col, sqlType(t, an), t, "True".equals(an.arg("null")) && !pk, pk || "True".equals(an.arg("unique")), pk, gen,
                        len, null, f.name()));
            }
            columns.put(d.table, cols);
            tableEntity.put(d.table, d.cx);
        }
        // many-to-many join tables
        for (Def d : byTable.values()) {
            for (FieldInfo f : d.fields) {
                if (f.annotations().isEmpty() || !f.annotations().get(0).name().equals("ManyToManyField")) continue;
                Ann an = f.annotations().get(0);
                if (an.arg("through") != null) continue;
                String ref = an.arg("to") != null ? an.arg("to") : an.arg("value");
                Cx target = f.resolvedClass() == null ? null : a.classesById.get(f.resolvedClass());
                String tTable, tPk, tPkType, tName;
                if (target != null && defs.containsKey(target)) {
                    Def td = defs.get(target);
                    tTable = td.table; tPk = td.pkName; tPkType = td.pkType; tName = target.name.toLowerCase(Locale.ROOT);
                } else if (ref != null && isBuiltinUserRef(ref)) {
                    tTable = "auth_user"; tPk = "id"; tPkType = "int"; tName = "user"; needBuiltinUser = true;
                } else continue;
                String join = an.arg("db_table") != null && PyParser.literal(an.arg("db_table")) != null ? PyParser.literal(an.arg("db_table")) : d.table + "_" + f.name();
                String mine = d.cx.name.toLowerCase(Locale.ROOT);
                String fromCol = (tTable.equals(d.table) ? "from_" + mine : mine) + "_id";
                String toCol = (tTable.equals(d.table) ? "to_" + tName : tName) + "_id";
                List<Column> jc = new ArrayList<>();
                jc.add(new Column("id", autoType, "AutoField", false, true, true, true, null, null, "id"));
                jc.add(new Column(fromCol, d.pkType, "ManyToManyField", false, false, false, false, null, d.table + "." + d.pkName, f.name()));
                jc.add(new Column(toCol, tPkType, "ManyToManyField", false, false, false, false, null, tTable + "." + tPk, f.name()));
                columns.putIfAbsent(join, jc);
                relations.add(new Relation(join, d.table, fromCol, d.pkName, "MANY_TO_ONE", "N:1", f.name(), join, null));
                relations.add(new Relation(join, tTable, toCol, tPk, "MANY_TO_ONE", "N:1", f.name(), join, null));
            }
        }
        if (needBuiltinUser && !columns.containsKey("auth_user")) {
            Def ud = new Def();
            List<Column> cols = new ArrayList<>();
            cols.add(new Column("id", "int", "AutoField", false, true, true, true, null, null, "id"));
            addBuiltinUserColumns(cols);
            columns.put("auth_user", cols);
        }

        List<Table> out = new ArrayList<>();
        for (Map.Entry<String, List<Column>> e : columns.entrySet()) {
            String name = e.getKey();
            Cx ent = tableEntity.get(name);
            List<String> refs = relations.stream().filter(r -> r.fromTable().equals(name)).map(Relation::toTable).distinct().toList();
            List<String> by = relations.stream().filter(r -> r.toTable().equals(name)).map(Relation::fromTable).distinct().toList();
            StringBuilder desc = new StringBuilder(ent != null ? "Stores " + ent.name + " records. " : "Table `" + name + "`. ");
            desc.append(e.getValue().size()).append(" column").append(e.getValue().size() == 1 ? "" : "s");
            List<String> pk = e.getValue().stream().filter(Column::primaryKey).map(Column::name).toList();
            if (!pk.isEmpty()) desc.append(", primary key ").append(String.join("+", pk));
            if (!refs.isEmpty()) desc.append(", references ").append(String.join(", ", refs));
            if (!by.isEmpty()) desc.append(", referenced by ").append(String.join(", ", by));
            desc.append('.');
            out.add(new Table(name, ent == null ? null : ent.id, ent == null ? null : ent.pf.path, ent == null ? 1 : ent.line, e.getValue(),
                    "Django ORM", desc.toString()));
        }
        Map<String, Relation> uniq = new LinkedHashMap<>();
        for (Relation r : relations) {
            if (!columns.containsKey(r.fromTable()) || !columns.containsKey(r.toTable())) continue;
            String label = r.toTable() + ("ONE_TO_ONE".equals(r.type()) ? " 1 ─ 1 " : " 1 ─ N ") + r.fromTable();
            uniq.putIfAbsent(r.fromTable() + "." + r.fromColumn() + ">" + r.toTable(),
                    new Relation(r.fromTable(), r.toTable(), r.fromColumn(), r.toColumn(), r.type(), r.cardinality(), r.viaField(), r.joinTable(), label));
        }
        return new Schema(out, new ArrayList<>(uniq.values()));
    }

    private static final Set<String> RELATION_TYPES = Set.of("ForeignKey", "OneToOneField", "ManyToManyField");

    private List<FieldInfo> effectiveFields(Cx m, Set<Cx> seen) {
        List<FieldInfo> out = new ArrayList<>();
        if (!seen.add(m)) return out;
        for (Cx b : m.bases) {
            if (isModel(b) && "True".equals(b.attrs.get("meta.abstract"))) out.addAll(effectiveFields(b, seen));
        }
        for (FieldInfo f : m.fields) {
            if (f.isStatic() || f.annotations().isEmpty()) continue;
            String t = f.annotations().get(0).name();
            if (!t.endsWith("Field") && !RELATION_TYPES.contains(t) && !t.equals("GenericForeignKey")) continue;
            final String n = f.name();
            out.removeIf(x -> x.name().equals(n));
            out.add(f);
        }
        return out;
    }

    private Cx concreteParent(Cx m, Map<Cx, Def> defs) {
        for (Cx b : m.bases) {
            if (isModel(b) && !"True".equals(b.attrs.get("meta.abstract")) && !"True".equals(m.attrs.get("meta.proxy")) && defs.containsKey(b)) return b;
        }
        return null;
    }

    private void addAuthColumns(Def d, List<Column> cols) {
        Set<String> bases = a.allBases(d.cx);
        if (bases.contains("AbstractUser")) addBuiltinUserColumns(cols);
        else if (bases.contains("AbstractBaseUser")) {
            cols.add(new Column("password", "varchar(128)", "CharField", false, false, false, false, 128, null, "password"));
            cols.add(new Column("last_login", "datetime", "DateTimeField", true, false, false, false, null, null, "last_login"));
            if (bases.contains("PermissionsMixin")) cols.add(new Column("is_superuser", "bool", "BooleanField", false, false, false, false, null, null, "is_superuser"));
        }
    }

    private static void addBuiltinUserColumns(List<Column> cols) {
        cols.add(new Column("password", "varchar(128)", "CharField", false, false, false, false, 128, null, "password"));
        cols.add(new Column("last_login", "datetime", "DateTimeField", true, false, false, false, null, null, "last_login"));
        cols.add(new Column("is_superuser", "bool", "BooleanField", false, false, false, false, null, null, "is_superuser"));
        cols.add(new Column("username", "varchar(150)", "CharField", false, true, false, false, 150, null, "username"));
        cols.add(new Column("first_name", "varchar(150)", "CharField", false, false, false, false, 150, null, "first_name"));
        cols.add(new Column("last_name", "varchar(150)", "CharField", false, false, false, false, 150, null, "last_name"));
        cols.add(new Column("email", "varchar(254)", "EmailField", false, false, false, false, 254, null, "email"));
        cols.add(new Column("is_staff", "bool", "BooleanField", false, false, false, false, null, null, "is_staff"));
        cols.add(new Column("is_active", "bool", "BooleanField", false, false, false, false, null, null, "is_active"));
        cols.add(new Column("date_joined", "datetime", "DateTimeField", false, false, false, false, null, null, "date_joined"));
    }

    private static String columnNameOf(FieldInfo f) {
        Ann an = f.annotations().isEmpty() ? null : f.annotations().get(0);
        if (an != null && an.arg("db_column") != null && PyParser.literal(an.arg("db_column")) != null) return PyParser.literal(an.arg("db_column"));
        return an != null && RELATION_TYPES.contains(an.name()) ? f.name() + "_id" : f.name();
    }

    private static Integer intOf(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s.strip()); } catch (NumberFormatException e) { return null; }
    }

    static String sqlType(String t, Ann an) {
        Integer len = intOf(an.arg("max_length"));
        return switch (t) {
            case "CharField" -> "varchar(" + (len == null ? 255 : len) + ")";
            case "EmailField" -> "varchar(" + (len == null ? 254 : len) + ")";
            case "SlugField" -> "varchar(" + (len == null ? 50 : len) + ")";
            case "URLField" -> "varchar(" + (len == null ? 200 : len) + ")";
            case "FileField", "ImageField", "FilePathField" -> "varchar(" + (len == null ? 100 : len) + ")";
            case "TextField" -> "text";
            case "IntegerField", "PositiveIntegerField" -> "int";
            case "BigIntegerField", "PositiveBigIntegerField" -> "bigint";
            case "SmallIntegerField", "PositiveSmallIntegerField" -> "smallint";
            case "AutoField" -> "int";
            case "BigAutoField" -> "bigint";
            case "SmallAutoField" -> "smallint";
            case "FloatField" -> "double";
            case "DecimalField" -> "decimal(" + (intOf(an.arg("max_digits")) == null ? 10 : intOf(an.arg("max_digits"))) + ","
                    + (intOf(an.arg("decimal_places")) == null ? 2 : intOf(an.arg("decimal_places"))) + ")";
            case "BooleanField", "NullBooleanField" -> "bool";
            case "DateField" -> "date";
            case "DateTimeField" -> "datetime";
            case "TimeField" -> "time";
            case "DurationField" -> "bigint";
            case "UUIDField" -> "char(32)";
            case "JSONField" -> "json";
            case "BinaryField" -> "blob";
            case "GenericIPAddressField", "IPAddressField" -> "char(39)";
            default -> "varchar(255)";
        };
    }

    // ═════════════════════════ security ═════════════════════════

    private static final Map<String, String> MIDDLEWARE_TEXT = Map.ofEntries(
            Map.entry("SecurityMiddleware", "Adds security headers and can redirect HTTP to HTTPS."),
            Map.entry("SessionMiddleware", "Loads the user's session from the session cookie."),
            Map.entry("CommonMiddleware", "Normalises URLs (APPEND_SLASH) and handles common HTTP details."),
            Map.entry("CsrfViewMiddleware", "Rejects state-changing requests (POST/PUT/DELETE) without a valid CSRF token."),
            Map.entry("AuthenticationMiddleware", "Attaches request.user — the logged-in user or an anonymous user."),
            Map.entry("MessageMiddleware", "Supports one-off flash messages between requests."),
            Map.entry("XFrameOptionsMiddleware", "Sets X-Frame-Options to block clickjacking."),
            Map.entry("CorsMiddleware", "Applies the CORS policy so a browser app on another origin may call the API."),
            Map.entry("WhiteNoiseMiddleware", "Serves static files efficiently."),
            Map.entry("LocaleMiddleware", "Chooses the language for the request."),
            Map.entry("GZipMiddleware", "Compresses responses."),
            Map.entry("RemoteUserMiddleware", "Authenticates using the web server's REMOTE_USER."));

    Security security(List<EndpointInfo> allEndpoints) {
        List<String> middleware = settingStrings("MIDDLEWARE");
        boolean djangoAuth = a.django && (settingStrings("INSTALLED_APPS").stream().anyMatch(s -> s.startsWith("django.contrib.auth"))
                || middleware.stream().anyMatch(s -> s.endsWith("AuthenticationMiddleware")));
        boolean anyAuth = accessByEndpoint.values().stream().anyMatch(x -> !"PUBLIC".equals(x.access()));
        if (!a.django && !anyAuth) return null;
        if (a.django && settingsFile == null && !anyAuth) return null;

        List<String> mechanisms = new ArrayList<>();
        List<String> authClasses = new ArrayList<>();
        Sett rf = setting("REST_FRAMEWORK");
        if (rf != null) {
            Matcher m = Pattern.compile("['\"]DEFAULT_AUTHENTICATION_CLASSES['\"]\\s*:\\s*[\\[(]([^\\])]*)[\\])]").matcher(rf.value);
            if (m.find()) PyParser.strings(m.group(1)).forEach(s -> authClasses.add(PythonAnalyzer.lastSeg(s)));
        }
        boolean session = middleware.stream().anyMatch(s -> s.endsWith("SessionMiddleware")) && djangoAuth;
        if (session) mechanisms.add("Session login (django.contrib.auth)");
        for (String ac : authClasses) {
            switch (ac) {
                case "TokenAuthentication" -> mechanisms.add("Token authentication");
                case "BasicAuthentication" -> mechanisms.add("HTTP Basic");
                case "JWTAuthentication", "JWTStatelessUserAuthentication" -> mechanisms.add("JWT Bearer token");
                case "SessionAuthentication" -> { if (!session) mechanisms.add("Session login (django.contrib.auth)"); }
                case "OAuth2Authentication" -> mechanisms.add("OAuth2");
                default -> mechanisms.add("Custom: " + ac);
            }
        }
        List<String> apps = settingStrings("INSTALLED_APPS");
        if (apps.stream().anyMatch(s -> s.startsWith("allauth"))) mechanisms.add("django-allauth (social / e-mail login)");
        if (apps.stream().anyMatch(s -> s.startsWith("oauth2_provider"))) mechanisms.add("OAuth2 provider");
        if (apps.stream().anyMatch(s -> s.startsWith("rest_framework.authtoken")) && !mechanisms.contains("Token authentication")) mechanisms.add("Token authentication");
        if (!a.django) {
            for (PF pf : a.files.values()) for (PyParser.Imp i : pf.mod.imports) {
                String mm = i.module() == null ? "" : i.module();
                if (mm.startsWith("flask_login")) mechanisms.add("Session login (Flask-Login)");
                if (mm.startsWith("flask_jwt_extended") || mm.startsWith("jose") || mm.equals("jwt")) mechanisms.add("JWT Bearer token");
                if (i.name() != null && (i.name().equals("OAuth2PasswordBearer") || i.name().equals("HTTPBearer"))) mechanisms.add("Bearer token");
            }
        }
        List<String> mech = mechanisms.stream().distinct().toList();

        // filter chain = middleware in order
        List<SecurityFilter> chain = new ArrayList<>();
        int order = 1;
        Sett mw = setting("MIDDLEWARE");
        for (String dotted : middleware) {
            String name = PythonAnalyzer.lastSeg(dotted);
            int i = dotted.lastIndexOf('.');
            Cx c = i < 0 ? null : classAt(dotted.substring(0, i), name);
            String desc = MIDDLEWARE_TEXT.get(name);
            if (desc == null) desc = c != null && c.doc != null && !c.doc.isBlank() ? c.doc.split("\\n")[0] : "Middleware that wraps every request and response.";
            chain.add(new SecurityFilter(order++, name, c == null ? null : c.id, c == null ? (mw == null ? null : mw.file.path) : c.pf.path,
                    c == null ? (mw == null ? 1 : mw.line) : c.line, desc));
        }
        if (!authClasses.isEmpty()) {
            chain.add(new SecurityFilter(order++, "DRF " + String.join(" / ", authClasses), null, rf.file.path, rf.line,
                    "Django REST Framework tries these authentication classes in order to identify the caller."));
        }
        List<String> defPerms = drfDefaultPermissions();
        chain.add(new SecurityFilter(order, "AuthorizationFilter", settingsFile == null || settingsFile.moduleCx == null ? null : settingsFile.moduleCx.id,
                rf != null ? rf.file.path : (settingsFile == null ? null : settingsFile.path), rf != null ? rf.line : 1,
                "Each view then enforces its own rules (@login_required, permission_classes …)"
                        + (defPerms.isEmpty() ? "" : "; project default: " + String.join(", ", defPerms)) + "."));

        // rules: endpoints with explicit protection first, then the default
        List<AuthRule> rules = new ArrayList<>();
        Set<String> seenRule = new HashSet<>();
        String defaultRead = defPerms.isEmpty() ? "PUBLIC" : mapPermissions(defPerms, "GET", "").access();
        String defaultWrite = defPerms.isEmpty() ? "PUBLIC" : mapPermissions(defPerms, "POST", "").access();
        for (EndpointInfo e : allEndpoints) {
            Acc acc = accessByEndpoint.get(e.id());
            if (acc == null || !acc.explicit()) continue; // only views that set their own rule
            if (!seenRule.add(e.httpMethod() + " " + e.path() + " " + acc.access())) continue;
            rules.add(new AuthRule(e.path(), e.httpMethod(), acc.access(), e.file(), e.line()));
        }
        String ruleFile = rf != null ? rf.file.path : settingsFile == null ? null : settingsFile.path;
        int ruleLine = rf == null ? 1 : rf.line;
        if (!defaultRead.equals(defaultWrite)) rules.add(new AuthRule("/**", "GET", defaultRead, ruleFile, ruleLine));
        rules.add(new AuthRule("/**", null, defaultWrite, ruleFile, ruleLine));

        // csrf / cors / sessions / password hashing
        boolean csrfMw = middleware.stream().anyMatch(s -> s.endsWith("CsrfViewMiddleware"));
        String csrf = !a.django ? null : csrfMw ? "enabled" : "disabled";
        String cors = null;
        if (apps.stream().anyMatch(s -> s.startsWith("corsheaders")) || middleware.stream().anyMatch(s -> s.contains("corsheaders"))) {
            boolean all = "True".equals(setting("CORS_ALLOW_ALL_ORIGINS") == null ? "" : setting("CORS_ALLOW_ALL_ORIGINS").value.strip())
                    || "True".equals(setting("CORS_ORIGIN_ALLOW_ALL") == null ? "" : setting("CORS_ORIGIN_ALLOW_ALL").value.strip());
            List<String> origins = settingStrings("CORS_ALLOWED_ORIGINS");
            cors = all ? "django-cors-headers: all origins allowed" : origins.isEmpty() ? "django-cors-headers (default: none)" : "django-cors-headers: " + String.join(", ", origins);
        } else if (a.django) cors = "default (same-origin only)";
        String sessionPolicy = a.django ? (session ? "SESSION (cookie)" : (mech.isEmpty() ? null : "STATELESS")) : null;
        String hasher = settingStrings("PASSWORD_HASHERS").stream().findFirst().map(PythonAnalyzer::lastSeg).orElse(a.django && djangoAuth ? "PBKDF2PasswordHasher (Django default)" : null);
        Cx user = userModel();
        String userSvc = !a.django ? null : user != null ? user.id : djangoAuth ? "django.contrib.auth.models.User" : null;
        String token = apps.stream().anyMatch(s -> s.startsWith("rest_framework_simplejwt")) || authClasses.stream().anyMatch(s -> s.startsWith("JWT")) ? "djangorestframework-simplejwt"
                : mech.contains("Token authentication") ? "rest_framework.authtoken (Token model)" : null;
        String login = allEndpoints.stream().filter(e -> e.path().toLowerCase(Locale.ROOT).matches(".*(login|signin|token|authenticate).*"))
                .filter(e -> e.httpMethod().equals("POST")).map(EndpointInfo::id).findFirst()
                .orElse(allEndpoints.stream().filter(e -> e.path().toLowerCase(Locale.ROOT).matches(".*(login|signin).*")).map(EndpointInfo::id).findFirst().orElse(null));

        List<String> n = new ArrayList<>();
        if (a.django) {
            Sett debug = setting("DEBUG");
            if (debug != null && debug.value.strip().equals("True")) n.add("DEBUG = True — fine for development, but must be False in production (it exposes detailed error pages).");
            Sett sk = setting("SECRET_KEY");
            if (sk != null && PyParser.literal(sk.value) != null) n.add("SECRET_KEY is written directly in " + sk.file.path + " — move it to an environment variable before deploying.");
            List<String> hosts = settingStrings("ALLOWED_HOSTS");
            if (hosts.contains("*")) n.add("ALLOWED_HOSTS contains '*' — any host name is accepted.");
            if (!csrfMw) n.add("CsrfViewMiddleware is not enabled, so cookie-based forms are not protected against CSRF.");
            if (csrfExempt > 0) n.add(csrfExempt + " view(s) use @csrf_exempt (CSRF check disabled for them).");
            if (!settingStrings("AUTH_PASSWORD_VALIDATORS").isEmpty() || setting("AUTH_PASSWORD_VALIDATORS") != null) n.add("Password validators are configured (AUTH_PASSWORD_VALIDATORS).");
            for (String s : notes) if (s.startsWith("Django's built-in")) n.add(s);
        }
        long publicWrites = allEndpoints.stream().filter(e -> Set.of("POST", "PUT", "PATCH", "DELETE").contains(e.httpMethod()) && "PUBLIC".equals(e.access())).count();
        if (publicWrites > 0) n.add(publicWrites + " state-changing endpoint(s) are public (no login required).");

        StringBuilder sum = new StringBuilder(a.django ? "Django authentication is " + (djangoAuth ? "enabled" : "not configured") : "Authentication checks found in the code");
        if (!mech.isEmpty()) sum.append(" using ").append(String.join(" + ", mech));
        sum.append('.');
        long protectedCount = allEndpoints.stream().filter(e -> !"PUBLIC".equals(e.access())).count();
        sum.append(' ').append(protectedCount).append(" of ").append(allEndpoints.size()).append(" endpoints require a login or permission.");
        if (hasher != null) sum.append(" Passwords are hashed with ").append(hasher).append('.');
        return new Security(true, mech, sum.toString(), chain, rules, sessionPolicy, csrf, cors, hasher, userSvc, token, login,
                settingsFile == null || settingsFile.moduleCx == null ? null : settingsFile.moduleCx.id, n);
    }
}
