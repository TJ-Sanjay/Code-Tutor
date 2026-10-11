package com.codetutor.backend.analysis;

import com.codetutor.backend.analysis.model.AnalysisModel.*;
import com.codetutor.backend.analysis.util.TypeUtil;

import java.util.*;
import java.util.stream.Collectors;

/** Builds the knowledge graph (nodes + typed edges) and the module list from the analysed classes. */
public class GraphBuilder {

    public record Graph(List<GraphNode> nodes, List<GraphEdge> edges) {}

    public List<ModuleInfo> modules(List<ClassInfo> classes, List<EndpointInfo> endpoints, Schema schema,
                                    List<FileInfo> files) {
        Map<String, List<ClassInfo>> by = new TreeMap<>();
        for (ClassInfo c : classes) {
            if (c.test() || c.nested()) continue;
            by.computeIfAbsent(c.module(), k -> new ArrayList<>()).add(c);
        }
        Map<String, Integer> fileCount = new HashMap<>();
        for (FileInfo f : files) if (f.module() != null && !f.module().isBlank()) fileCount.merge(f.module(), 1, Integer::sum);

        Map<String, String> entityTable = new HashMap<>();
        if (schema != null) for (Table t : schema.tables()) if (t.entity() != null) entityTable.put(t.entity(), t.name());

        List<ModuleInfo> out = new ArrayList<>();
        for (var e : by.entrySet()) {
            Map<String, List<String>> layers = new LinkedHashMap<>();
            for (ClassInfo c : e.getValue()) layers.computeIfAbsent(c.layer(), k -> new ArrayList<>()).add(c.name());
            Set<String> ids = e.getValue().stream().map(ClassInfo::id).collect(Collectors.toSet());
            int eps = (int) endpoints.stream().filter(x -> ids.contains(x.controller())).count();
            List<String> tables = e.getValue().stream().map(c -> entityTable.get(c.id())).filter(Objects::nonNull)
                    .distinct().toList();
            out.add(new ModuleInfo(e.getKey(), e.getValue().stream().map(ClassInfo::id).toList(), layers, eps, tables,
                    fileCount.getOrDefault(e.getKey(), 0), describeModule(e.getKey(), layers, eps, tables)));
        }
        return out;
    }

    private String describeModule(String name, Map<String, List<String>> layers, int eps, List<String> tables) {
        List<String> parts = new ArrayList<>();
        for (var l : layers.entrySet()) {
            int n = l.getValue().size();
            String[] words = switch (l.getKey()) {
                case "CONTROLLER" -> new String[]{"controller", "controllers"};
                case "SERVICE" -> new String[]{"service", "services"};
                case "REPOSITORY" -> new String[]{"repository", "repositories"};
                case "ENTITY" -> new String[]{"entity", "entities"};
                case "CONFIG" -> new String[]{"configuration class", "configuration classes"};
                case "SECURITY" -> new String[]{"security class", "security classes"};
                case "APPLICATION" -> new String[]{"application entry point", "application entry points"};
                case "DTO" -> new String[]{"DTO", "DTOs"};
                case "COMPONENT" -> new String[]{"component", "components"};
                case "MODEL" -> new String[]{"model class", "model classes"};
                case "EXCEPTION" -> new String[]{"exception", "exceptions"};
                case "UTIL" -> new String[]{"utility class", "utility classes"};
                case "ADVICE" -> new String[]{"exception handler", "exception handlers"};
                default -> new String[]{"other class", "other classes"};
            };
            parts.add(n + " " + (n == 1 ? words[0] : words[1]));
        }
        StringBuilder sb = new StringBuilder("Module “" + name + "”: " + String.join(", ", parts) + ".");
        if (eps > 0) sb.append(" Exposes ").append(eps).append(" endpoint").append(eps > 1 ? "s" : "").append('.');
        if (!tables.isEmpty()) sb.append(" Manages table").append(tables.size() > 1 ? "s " : " ").append(String.join(", ", tables)).append('.');
        return sb.toString();
    }

    public Graph graph(List<ClassInfo> classes, List<EndpointInfo> endpoints, Schema schema, List<ModuleInfo> modules,
                       Frontend frontend) {
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();
        Map<String, ClassInfo> byId = new HashMap<>();
        for (ClassInfo c : classes) byId.put(c.id(), c);
        Set<String> included = new HashSet<>();

        for (ModuleInfo m : modules) {
            nodes.add(new GraphNode("module:" + m.name(), "MODULE", m.name(), null, m.name(), null, 0,
                    m.classes().size() + " classes"));
        }
        for (ClassInfo c : classes) {
            if (c.test() || c.nested()) continue;
            included.add(c.id());
            nodes.add(new GraphNode(c.id(), "CLASS", c.name(), c.layer(), c.module(), c.file(), c.line(),
                    c.kind().toLowerCase(Locale.ROOT)));
            edges.add(new GraphEdge("contains:" + c.id(), "module:" + c.module(), c.id(), "CONTAINS", "contains"));
        }
        int n = 0;
        for (ClassInfo c : classes) {
            if (!included.contains(c.id())) continue;
            for (Dep d : c.dependsOn()) {
                ClassInfo t = byId.get(d.target());
                if (t == null || !included.contains(t.id())) continue;
                String type;
                String label;
                switch (d.kind()) {
                    case "EXTENDS" -> { type = "EXTENDS"; label = "extends"; }
                    case "IMPLEMENTS" -> { type = "IMPLEMENTS"; label = "implements"; }
                    case "INJECTS" -> { type = "DEPENDS_ON"; label = "injects"; }
                    case "CALLS" -> { type = "DEPENDS_ON"; label = "calls"; }
                    default -> {
                        type = "USES";
                        label = "REPOSITORY".equals(c.layer()) && "ENTITY".equals(t.layer()) ? "manages" : "uses";
                    }
                }
                edges.add(new GraphEdge("e" + n++, c.id(), t.id(), type, label));
            }
        }
        if (schema != null) {
            for (Table t : schema.tables()) {
                nodes.add(new GraphNode("table:" + t.name(), "TABLE", t.name(), "TABLE", null, t.file(), t.line(),
                        t.columns().size() + " columns"));
                if (t.entity() != null && included.contains(t.entity())) {
                    edges.add(new GraphEdge("maps:" + t.entity(), t.entity(), "table:" + t.name(), "MAPS_TO", "maps to"));
                }
            }
            for (Relation r : schema.relations()) {
                edges.add(new GraphEdge("fk:" + r.fromTable() + "." + r.fromColumn(), "table:" + r.fromTable(),
                        "table:" + r.toTable(), "REFERENCES", r.cardinality()));
            }
        }
        for (EndpointInfo e : endpoints) {
            nodes.add(new GraphNode("endpoint:" + e.id(), "ENDPOINT", e.httpMethod() + " " + e.path(), "ENDPOINT",
                    byId.containsKey(e.controller()) ? byId.get(e.controller()).module() : null, e.file(), e.line(),
                    e.access()));
            edges.add(new GraphEdge("handles:" + e.id(), "endpoint:" + e.id(), e.controller(), "HANDLED_BY", "handled by"));
        }
        if (frontend != null && frontend.present()) {
            Set<String> jsIds = frontend.files().stream().map(JsFile::path).collect(Collectors.toSet());
            for (JsFile f : frontend.files()) {
                nodes.add(new GraphNode("js:" + f.path(), "JSFILE", f.path().substring(f.path().lastIndexOf('/') + 1),
                        "FRONTEND", null, f.path(), 1, f.kind()));
                for (String imp : f.imports()) {
                    if (jsIds.contains(imp)) edges.add(new GraphEdge("imp:" + f.path() + ">" + imp, "js:" + f.path(), "js:" + imp,
                            "IMPORTS", "imports"));
                }
                for (ApiCall a : f.apiCalls()) {
                    if (a.endpointId() != null) {
                        edges.add(new GraphEdge("api:" + f.path() + ":" + a.line(), "js:" + f.path(), "endpoint:" + a.endpointId(),
                                "CALLS_API", a.method() + " " + a.path()));
                    }
                }
            }
        }
        return new Graph(nodes, edges);
    }

    public static String nameOf(String id) { return TypeUtil.simpleName(id); }
}
