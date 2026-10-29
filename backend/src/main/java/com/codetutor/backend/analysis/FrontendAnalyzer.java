package com.codetutor.backend.analysis;

import com.codetutor.backend.analysis.model.AnalysisModel.*;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Light-weight analysis of the React / JS part of a full-stack project: components, imports between files
 * and the HTTP calls (axios / fetch) the UI makes — which are then linked to backend endpoints so the data flow
 * can start in the browser.
 */
public class FrontendAnalyzer {

    private static final Pattern IMPORT = Pattern.compile(
            "import\\s+(?:[^'\"]*?\\sfrom\\s+)?['\"]([^'\"]+)['\"]");
    private static final Pattern COMPONENT_FN = Pattern.compile(
            "(?:export\\s+default\\s+|export\\s+)?function\\s+([A-Z]\\w*)\\s*\\(");
    private static final Pattern COMPONENT_CONST = Pattern.compile(
            "(?:export\\s+)?const\\s+([A-Z]\\w*)\\s*=\\s*(?:React\\.)?(?:memo\\(|forwardRef\\()?\\s*(?:async\\s*)?\\(?[\\w{},\\s]*\\)?\\s*=>");
    private static final Pattern COMPONENT_CLASS = Pattern.compile(
            "class\\s+([A-Z]\\w*)\\s+extends\\s+(?:React\\.)?(?:Pure)?Component");
    private static final Pattern AXIOS_CALL = Pattern.compile(
            "\\b(?:api|axios|http|client|apiClient|instance)\\s*\\.\\s*(get|post|put|delete|patch)\\s*\\(\\s*(`[^`]*`|'[^']*'|\"[^\"]*\")");
    private static final Pattern FETCH_CALL = Pattern.compile("\\bfetch\\s*\\(\\s*(`[^`]*`|'[^']*'|\"[^\"]*\")([^)]{0,240})");

    private final AntPathMatcher matcher = new AntPathMatcher();

    public Frontend analyze(Path root, List<String> jsFiles, List<EndpointInfo> endpoints, String contextPath) {
        if (jsFiles.isEmpty()) return new Frontend(false, null, null, List.of());

        Set<String> all = new HashSet<>(jsFiles);
        List<JsFile> out = new ArrayList<>();
        boolean react = false;
        Set<String> roots = new TreeSet<>();
        for (String f : jsFiles) {
            String text;
            try {
                text = Files.readString(root.resolve(f));
            } catch (IOException e) {
                continue;
            }
            if (text.contains("from 'react'") || text.contains("from \"react\"") || f.endsWith(".jsx") || f.endsWith(".tsx")) {
                react = true;
            }
            int slash = f.indexOf("/src/");
            if (slash >= 0) roots.add(f.substring(0, slash));
            else if (f.startsWith("src/")) roots.add("");

            List<String> comps = new ArrayList<>();
            for (Pattern p : List.of(COMPONENT_FN, COMPONENT_CONST, COMPONENT_CLASS)) {
                Matcher m = p.matcher(text);
                while (m.find()) if (!comps.contains(m.group(1))) comps.add(m.group(1));
            }
            List<String> imports = new ArrayList<>();
            Matcher im = IMPORT.matcher(text);
            while (im.find()) {
                String spec = im.group(1);
                if (!spec.startsWith(".")) continue;
                String resolved = resolve(f, spec, all);
                if (resolved != null && !imports.contains(resolved)) imports.add(resolved);
            }
            List<ApiCall> calls = apiCalls(text, endpoints, contextPath);

            String kind = kindOf(f, text, comps, calls);
            out.add(new JsFile(f, comps, imports, calls, kind, 1, text.split("\n", -1).length,
                    summary(f, kind, comps, calls)));
        }
        String frameworkName = react ? "React" : "JavaScript";
        String r = roots.isEmpty() ? "" : roots.iterator().next();
        return new Frontend(true, r, frameworkName, out);
    }

    private String resolve(String from, String spec, Set<String> all) {
        Path base = Path.of(from).getParent();
        Path target = (base == null ? Path.of(spec) : base.resolve(spec)).normalize();
        String t = target.toString().replace('\\', '/');
        for (String cand : List.of(t, t + ".jsx", t + ".js", t + ".tsx", t + ".ts", t + "/index.jsx", t + "/index.js",
                t + "/index.tsx", t + "/index.ts")) {
            if (all.contains(cand)) return cand;
        }
        return null;
    }

    private List<ApiCall> apiCalls(String text, List<EndpointInfo> endpoints, String contextPath) {
        List<ApiCall> out = new ArrayList<>();
        Matcher m = AXIOS_CALL.matcher(text);
        while (m.find()) {
            addCall(out, m.group(1).toUpperCase(Locale.ROOT), m.group(2), lineOf(text, m.start()), endpoints, contextPath);
        }
        Matcher f = FETCH_CALL.matcher(text);
        while (f.find()) {
            String rest = f.group(2);
            Matcher mm = Pattern.compile("method\\s*:\\s*['\"](\\w+)['\"]").matcher(rest);
            String method = mm.find() ? mm.group(1).toUpperCase(Locale.ROOT) : "GET";
            addCall(out, method, f.group(1), lineOf(text, f.start()), endpoints, contextPath);
        }
        return out;
    }

    private void addCall(List<ApiCall> out, String method, String literal, int line, List<EndpointInfo> endpoints,
                         String contextPath) {
        String path = literal.substring(1, literal.length() - 1);
        path = path.replaceAll("\\$\\{[^}]*}", "{var}");
        path = path.replaceFirst("^https?://[^/]+", "");
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        if (!path.startsWith("/")) return;
        String norm = path;
        if (!contextPath.isEmpty() && norm.startsWith(contextPath)) norm = norm.substring(contextPath.length());
        else if (norm.startsWith("/api/") ) norm = norm.substring(4);
        if (norm.isEmpty()) norm = "/";
        String endpoint = matchEndpoint(method, norm, endpoints);
        // Django / FastAPI projects usually keep the /api prefix in the route itself
        if (endpoint == null && !norm.equals(path)) endpoint = matchEndpoint(method, path, endpoints);
        out.add(new ApiCall(method, norm, line, endpoint));
    }

    private String matchEndpoint(String method, String path, List<EndpointInfo> endpoints) {
        String callPattern = trimSlash(path.replaceAll("\\{[^}]*}", "*"));
        EndpointInfo best = null;
        for (EndpointInfo e : endpoints) {
            if (!e.httpMethod().equals(method) && !"ANY".equals(e.httpMethod())) continue;
            String epPattern = trimSlash(e.path().replaceAll("\\{[^}]*}", "*"));
            if (matcher.match(epPattern, callPattern) || epPattern.equals(callPattern)) {
                best = e;
                break;
            }
        }
        return best != null ? best.id() : null;
    }

    /** "/owners/" and "/owners" are the same route (Django APPEND_SLASH). */
    private static String trimSlash(String p) {
        return p.length() > 1 && p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
    }

    private static int lineOf(String text, int idx) {
        int line = 1;
        for (int i = 0; i < idx && i < text.length(); i++) if (text.charAt(i) == '\n') line++;
        return line;
    }

    private String kindOf(String file, String text, List<String> comps, List<ApiCall> calls) {
        String name = file.substring(file.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (text.contains("createRoot(") || text.contains("ReactDOM.render")) return "ENTRY";
        if (name.startsWith("api.") || name.contains("service") || (!calls.isEmpty() && comps.isEmpty())) return "API";
        if (text.contains("<Routes") || text.contains("createBrowserRouter") || text.contains("<Route ")) return "ROUTER";
        if (file.contains("/pages/") || file.contains("/views/") || file.contains("/screens/")) return "PAGE";
        if (name.startsWith("use") && comps.isEmpty()) return "HOOK";
        if (!comps.isEmpty()) return "COMPONENT";
        return "MODULE";
    }

    private String summary(String file, String kind, List<String> comps, List<ApiCall> calls) {
        String name = file.substring(file.lastIndexOf('/') + 1);
        StringBuilder sb = new StringBuilder();
        switch (kind) {
            case "ENTRY" -> sb.append("Application entry point: mounts the React app into the page.");
            case "API" -> sb.append("API client module: configures how the UI talks to the backend.");
            case "ROUTER" -> sb.append("Defines the client-side routes of the app.");
            case "PAGE" -> sb.append("Page-level React component (").append(String.join(", ", comps)).append(").");
            case "HOOK" -> sb.append("Custom React hook providing reusable stateful logic.");
            case "COMPONENT" -> sb.append("React component").append(comps.size() > 1 ? "s " : " ")
                    .append(String.join(", ", comps)).append('.');
            default -> sb.append("JavaScript module ").append(name).append('.');
        }
        if (!calls.isEmpty()) {
            sb.append(" Calls ").append(calls.size()).append(" backend endpoint").append(calls.size() > 1 ? "s" : "")
                    .append(": ").append(calls.stream().limit(3).map(c -> c.method() + " " + c.path())
                            .reduce((a, b) -> a + ", " + b).orElse("")).append(calls.size() > 3 ? " …" : "");
        }
        return sb.toString();
    }
}
