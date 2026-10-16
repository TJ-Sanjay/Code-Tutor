package com.codetutor.backend.analysis.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Immutable data model produced by the analysis pipeline. The whole {@link Result} is serialised
 * to JSON on disk (one file per project) and is the single source of truth for the knowledge
 * graph, the diagrams, the explain popups and the chat retrieval.
 */
public final class AnalysisModel {

    private AnalysisModel() {}

    /** Annotation with its (string-rendered) arguments, e.g. @GetMapping("/owners") -> value="/owners". */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Ann(String name, Map<String, String> args) {
        public String arg(String key) { return args == null ? null : args.get(key); }
        public String render() {
            if (args == null || args.isEmpty()) return "@" + name;
            if (args.size() == 1 && args.containsKey("value")) return "@" + name + "(" + args.get("value") + ")";
            StringBuilder sb = new StringBuilder("@" + name + "(");
            boolean first = true;
            for (var e : args.entrySet()) {
                if (!first) sb.append(", ");
                sb.append(e.getKey()).append("=").append(e.getValue());
                first = false;
            }
            return sb.append(")").toString();
        }
    }

    public record Param(String name, String type) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Call(String targetClass, String targetClassName, String method, int line, String targetMethodId,
                       boolean library) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FieldInfo(String name, String type, int line, List<Ann> annotations, boolean injected,
                            String resolvedClass, boolean isStatic, boolean isFinal) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MethodInfo(String id, String name, String signature, String returnType, List<Param> params,
                             int line, int endLine, List<Ann> annotations, List<String> modifiers,
                             String doc, List<Call> calls, boolean constructor, String endpointId, int loc) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Dep(String target, String kind) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ClassInfo(String id, String name, String pkg, String file, int line, int endLine, String kind,
                            String layer, String module, List<Ann> annotations, String extendsType,
                            List<String> implementsTypes, List<FieldInfo> fields, List<MethodInfo> methods,
                            String doc, List<Dep> dependsOn, boolean test, Map<String, String> attrs,
                            List<String> imports, String summary) {
        @com.fasterxml.jackson.annotation.JsonIgnore
        public boolean nested() { return attrs != null && "true".equals(attrs.get("nested")); }

        @com.fasterxml.jackson.annotation.JsonIgnore
        public int outerRank() { return nested() ? 1 : 0; }

        public ClassInfo withSummary(String s) {
            return new ClassInfo(id, name, pkg, file, line, endLine, kind, layer, module, annotations, extendsType,
                    implementsTypes, fields, methods, doc, dependsOn, test, attrs, imports, s);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EndpointParam(String name, String type, String source, boolean required) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EndpointInfo(String id, String httpMethod, String path, String fullPath, String controller,
                               String handler, String file, int line, List<EndpointParam> params, String returns,
                               String access, String accessNote, String description, boolean viewResponse) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Column(String name, String type, String javaType, boolean nullable, boolean unique,
                         boolean primaryKey, boolean generated, Integer length, String foreignKey, String field) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Table(String name, String entity, String file, int line, List<Column> columns, String source,
                        String description) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Relation(String fromTable, String toTable, String fromColumn, String toColumn, String type,
                           String cardinality, String viaField, String joinTable, String label) {}

    public record Schema(List<Table> tables, List<Relation> relations) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ModuleInfo(String name, List<String> classes, Map<String, List<String>> layers, int endpoints,
                             List<String> tables, int files, String description) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SecurityFilter(int order, String name, String classId, String file, int line, String description) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuthRule(String pattern, String httpMethod, String access, String file, int line) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Security(boolean enabled, List<String> mechanisms, String summary, List<SecurityFilter> filters,
                           List<AuthRule> rules, String sessionPolicy, String csrf, String cors,
                           String passwordEncoder, String userDetailsService, String tokenService,
                           String loginEndpoint, String configClass, List<String> notes) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiCall(String method, String path, int line, String endpointId) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JsFile(String path, List<String> components, List<String> imports, List<ApiCall> apiCalls,
                         String kind, int line, int endLine, String summary) {}

    public record Frontend(boolean present, String root, String framework, List<JsFile> files) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Library(String name, String version, String scope, String source) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FileInfo(String path, String language, int lines, long size, String layer, String module,
                           String summary, List<String> classes) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GraphNode(String id, String type, String label, String layer, String module, String file,
                            int line, String subtitle) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GraphEdge(String id, String source, String target, String type, String label) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Chunk(String id, String type, String module, String layer, String name, String file,
                        int startLine, int endLine, String text, String classId, String methodId) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Overview(String name, String analyzedAt, int totalFiles, int sourceFiles, int totalLines,
                           int classCount, int methodCount, int endpointCount, int tableCount, int moduleCount,
                           String basePackage, Map<String, Double> languages, List<String> frameworks,
                           List<String> buildTools, List<String> databases, List<String> tools,
                           Map<String, Integer> layerCounts, List<String> sensitiveFiles, String summary,
                           List<String> highlights) {}

    public record Result(Overview overview, List<FileInfo> files, List<ClassInfo> classes,
                         List<EndpointInfo> endpoints, Schema schema, List<ModuleInfo> modules, Security security,
                         Frontend frontend, List<Library> libraries, List<GraphNode> nodes, List<GraphEdge> edges,
                         List<Chunk> chunks) {}

    // ───────────────────────── Flow (computed on demand) ─────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FlowStep(int index, int parent, String kind, String title, String subtitle, String file,
                           int line, int endLine, String description, String edgeLabel, String classId,
                           String methodId, String access) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Flow(String id, String title, String subtitle, String kind, List<FlowStep> steps,
                       List<String> notes, String narrative) {}
}
