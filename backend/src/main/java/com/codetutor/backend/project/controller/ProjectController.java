package com.codetutor.backend.project.controller;

import com.codetutor.backend.analysis.AnalysisPipeline;
import com.codetutor.backend.analysis.CodeFiles;
import com.codetutor.backend.analysis.Knowledge;
import com.codetutor.backend.analysis.model.AnalysisModel.*;
import com.codetutor.backend.chat.LlmClient;
import com.codetutor.backend.common.exception.NotFoundException;
import com.codetutor.backend.common.response.ApiResponse;
import com.codetutor.backend.explain.ExplainModel.Explanation;
import com.codetutor.backend.explain.ExplainResolver;
import com.codetutor.backend.explain.SemanticTokenService;
import com.codetutor.backend.project.dto.CreateGithubProjectRequest;
import com.codetutor.backend.project.dto.ProjectResponse;
import com.codetutor.backend.project.service.ProjectAccess;
import com.codetutor.backend.project.service.ProjectService;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Project REST API. Every handler resolves the project through {@link ProjectAccess}, which only ever returns
 * projects owned by the authenticated user — so one account can never read another account's code or analysis.
 */
@RestController
@RequestMapping("/projects")
public class ProjectController {

    private static final long MAX_VIEW_BYTES = 1_500_000;

    private final ProjectService projects;
    private final ProjectAccess access;
    private final LlmClient llm;

    public ProjectController(ProjectService projects, ProjectAccess access, LlmClient llm) {
        this.projects = projects;
        this.access = access;
        this.llm = llm;
    }

    // ───────────────────────── lifecycle ─────────────────────────

    @PostMapping("/github")
    public ResponseEntity<ApiResponse<ProjectResponse>> createFromGithub(@AuthenticationPrincipal UserDetails u,
                                                                         @Valid @RequestBody CreateGithubProjectRequest req) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success("Import started", projects.createFromGithub(u.getUsername(), req)));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProjectResponse>> createFromZip(@AuthenticationPrincipal UserDetails u,
                                                                      @RequestParam("name") String name,
                                                                      @RequestParam(value = "description", required = false) String description,
                                                                      @RequestParam("file") MultipartFile file) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Project name is required");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success("Upload received", projects.createFromZip(u.getUsername(), name, description, file)));
    }

    @GetMapping
    public ApiResponse<List<ProjectResponse>> list(@AuthenticationPrincipal UserDetails u) {
        return ApiResponse.success("Projects", projects.list(u.getUsername()));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectResponse> get(@AuthenticationPrincipal UserDetails u, @PathVariable long id) {
        return ApiResponse.success("Project", projects.get(u.getUsername(), id));
    }

    @GetMapping("/{id}/status")
    public ApiResponse<ProjectResponse> status(@AuthenticationPrincipal UserDetails u, @PathVariable long id) {
        return ApiResponse.success("Status", projects.get(u.getUsername(), id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserDetails u, @PathVariable long id) {
        projects.delete(u.getUsername(), id);
        return ApiResponse.success("Project deleted", null);
    }

    @PostMapping("/{id}/reanalyze")
    public ApiResponse<ProjectResponse> reanalyze(@AuthenticationPrincipal UserDetails u, @PathVariable long id) {
        return ApiResponse.success("Re-analysis started", projects.reanalyze(u.getUsername(), id));
    }

    public record SensitiveRequest(List<String> paths) {}

    @PostMapping("/{id}/sensitive-access")
    public ApiResponse<ProjectResponse> allowSensitive(@AuthenticationPrincipal UserDetails u, @PathVariable long id,
                                                       @RequestBody SensitiveRequest req) {
        return ApiResponse.success("Access granted — re-analysing", projects.allowSensitive(u.getUsername(), id,
                req.paths() == null ? List.of() : req.paths()));
    }

    // ───────────────────────── analysis data ─────────────────────────

    public record OverviewResponse(Overview overview, List<ModuleInfo> modules, List<Library> libraries, Security security,
                                   boolean hasFrontend, int frontendFiles, boolean llm) {}

    @GetMapping("/{id}/overview")
    public ApiResponse<OverviewResponse> overview(@AuthenticationPrincipal UserDetails u, @PathVariable long id) {
        Knowledge k = access.ready(u.getUsername(), id).k();
        Result r = k.result;
        return ApiResponse.success("Overview", new OverviewResponse(r.overview(), r.modules(), r.libraries(), r.security(),
                r.frontend() != null && r.frontend().present(), r.frontend() == null ? 0 : r.frontend().files().size(),
                llm != null && llm.enabled()));
    }

    @GetMapping("/{id}/graph")
    public ApiResponse<Map<String, Object>> graph(@AuthenticationPrincipal UserDetails u, @PathVariable long id) {
        Result r = access.ready(u.getUsername(), id).k().result;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nodes", r.nodes());
        out.put("edges", r.edges());
        out.put("modules", r.modules().stream().map(m -> Map.of("name", m.name(), "classes", m.classes().size(), "endpoints", m.endpoints(),
                "description", m.description())).toList());
        return ApiResponse.success("Knowledge graph", out);
    }

    @GetMapping("/{id}/endpoints")
    public ApiResponse<List<EndpointInfo>> endpoints(@AuthenticationPrincipal UserDetails u, @PathVariable long id) {
        return ApiResponse.success("Endpoints", access.ready(u.getUsername(), id).k().result.endpoints());
    }

    @GetMapping("/{id}/schema")
    public ApiResponse<Schema> schema(@AuthenticationPrincipal UserDetails u, @PathVariable long id) {
        return ApiResponse.success("Schema", access.ready(u.getUsername(), id).k().result.schema());
    }

    @GetMapping("/{id}/flow")
    public ApiResponse<Flow> flow(@AuthenticationPrincipal UserDetails u, @PathVariable long id, @RequestParam("endpoint") String endpoint) {
        Knowledge k = access.ready(u.getUsername(), id).k();
        Flow f = "security".equals(endpoint) ? k.flows().securityFlow() : k.flows().endpointFlow(endpoint);
        if (f == null) throw new NotFoundException("Endpoint not found");
        return ApiResponse.success("Flow", f);
    }

    public record ClassSummary(String id, String name, String layer, String module, String kind, String file, int line, String summary,
                               int methods, int dependents) {}

    @GetMapping("/{id}/classes")
    public ApiResponse<List<ClassSummary>> classes(@AuthenticationPrincipal UserDetails u, @PathVariable long id) {
        Knowledge k = access.ready(u.getUsername(), id).k();
        List<ClassSummary> out = k.result.classes().stream().filter(c -> !c.test() && !c.nested())
                .map(c -> new ClassSummary(c.id(), c.name(), c.layer(), c.module(), c.kind(), c.file(), c.line(), c.summary(),
                        c.methods().size(), k.dependentsOf(c.id()).size())).toList();
        return ApiResponse.success("Classes", out);
    }

    public record DepNode(String id, String name, String layer, String module, String relation, String file, int line,
                          boolean hasChildren, String summary) {}

    @GetMapping("/{id}/dependencies")
    public ApiResponse<List<DepNode>> dependencies(@AuthenticationPrincipal UserDetails u, @PathVariable long id,
                                                   @RequestParam(value = "class", required = false) String classId,
                                                   @RequestParam(value = "dir", defaultValue = "out") String dir) {
        Knowledge k = access.ready(u.getUsername(), id).k();
        List<DepNode> out = new ArrayList<>();
        if (classId == null || classId.isBlank()) {
            // roots: entry points of the system (controllers first, otherwise classes nobody depends on)
            List<ClassInfo> roots = k.result.classes().stream().filter(c -> !c.test() && !c.nested() && "CONTROLLER".equals(c.layer())).toList();
            if (roots.isEmpty()) {
                roots = k.result.classes().stream().filter(c -> !c.test() && !c.nested() && k.dependentsOf(c.id()).isEmpty()).toList();
            }
            for (ClassInfo c : roots) out.add(depNode(k, c, null, "out"));
            return ApiResponse.success("Roots", out);
        }
        ClassInfo c = k.classById.get(classId);
        if (c == null) throw new NotFoundException("Class not found");
        if ("in".equals(dir)) {
            for (ClassInfo d : k.dependentsOf(c.id())) {
                String rel = d.dependsOn().stream().filter(x -> x.target().equals(c.id())).map(Dep::kind).findFirst().orElse("USES");
                out.add(depNode(k, d, rel, "in"));
            }
        } else {
            for (Dep d : c.dependsOn()) {
                ClassInfo t = k.classById.get(d.target());
                if (t != null && !t.test() && !t.nested()) out.add(depNode(k, t, d.kind(), "out"));
            }
        }
        out.sort(Comparator.comparingInt((DepNode n) -> layerOrder(n.layer())).thenComparing(DepNode::name));
        return ApiResponse.success("Dependencies", out);
    }

    private DepNode depNode(Knowledge k, ClassInfo c, String rel, String dir) {
        boolean more = "in".equals(dir) ? !k.dependentsOf(c.id()).isEmpty()
                : c.dependsOn().stream().anyMatch(d -> k.classById.containsKey(d.target()) && !k.classById.get(d.target()).test()
                && !k.classById.get(d.target()).nested());
        return new DepNode(c.id(), c.name(), c.layer(), c.module(), rel, c.file(), c.line(), more, c.summary());
    }

    private static int layerOrder(String l) {
        return List.of("CONTROLLER", "SERVICE", "REPOSITORY", "ENTITY", "DTO", "COMPONENT", "SECURITY", "CONFIG").indexOf(l) < 0 ? 99
                : List.of("CONTROLLER", "SERVICE", "REPOSITORY", "ENTITY", "DTO", "COMPONENT", "SECURITY", "CONFIG").indexOf(l);
    }

    // ───────────────────────── explorer ─────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TreeNode(String name, String path, String type, Long size, Integer lines, String language, String layer,
                           List<TreeNode> children) {}

    @GetMapping("/{id}/files")
    public ApiResponse<TreeNode> files(@AuthenticationPrincipal UserDetails u, @PathVariable long id) {
        Knowledge k = access.ready(u.getUsername(), id).k();
        return ApiResponse.success("Files", buildTree(k.result.overview().name(), k.result.files()));
    }

    private static TreeNode buildTree(String rootName, List<FileInfo> files) {
        Map<String, Object> root = new TreeMap<>();
        Map<String, FileInfo> byPath = new HashMap<>();
        for (FileInfo f : files) {
            byPath.put(f.path(), f);
            String[] parts = f.path().split("/");
            Map<String, Object> cur = root;
            for (int i = 0; i < parts.length - 1; i++) {
                @SuppressWarnings("unchecked")
                Map<String, Object> next = (Map<String, Object>) cur.computeIfAbsent(parts[i], x -> new TreeMap<String, Object>());
                cur = next;
            }
            cur.put(parts[parts.length - 1], f);
        }
        return new TreeNode(rootName, "", "DIRECTORY", null, null, null, null, children(root, ""));
    }

    @SuppressWarnings("unchecked")
    private static List<TreeNode> children(Map<String, Object> dir, String prefix) {
        List<TreeNode> dirs = new ArrayList<>();
        List<TreeNode> fs = new ArrayList<>();
        for (var e : dir.entrySet()) {
            String path = prefix.isEmpty() ? e.getKey() : prefix + "/" + e.getKey();
            if (e.getValue() instanceof FileInfo f) {
                fs.add(new TreeNode(e.getKey(), path, "FILE", f.size(), f.lines(), f.language(), f.layer(), null));
            } else {
                dirs.add(new TreeNode(e.getKey(), path, "DIRECTORY", null, null, null, null, children((Map<String, Object>) e.getValue(), path)));
            }
        }
        dirs.addAll(fs);
        return dirs;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FileContent(String path, String language, String content, long size, int lines, boolean binary, boolean truncated,
                              String summary, String layer, String blockedReason) {}

    @GetMapping("/{id}/file")
    public ApiResponse<FileContent> file(@AuthenticationPrincipal UserDetails u, @PathVariable long id, @RequestParam("path") String rel) throws IOException {
        ProjectAccess.Ctx ctx = access.ready(u.getUsername(), id);
        Path p = safe(ctx.root(), rel);
        String name = p.getFileName().toString();
        Set<String> allowed = new HashSet<>();
        if (ctx.project().getAllowedSensitive() != null) allowed.addAll(Arrays.asList(ctx.project().getAllowedSensitive().split("\n")));
        FileInfo info = ctx.k().fileByPath.get(rel);
        if (AnalysisPipeline.isSensitive(name) && !allowed.contains(rel)) {
            return ApiResponse.success("Blocked", new FileContent(rel, "Other", "", 0, 0, false, false, null, null,
                    "This file may contain secrets, so it is not read unless you grant access."));
        }
        long size = Files.size(p);
        byte[] data;
        try (var in = Files.newInputStream(p)) {
            data = in.readNBytes((int) Math.min(size, MAX_VIEW_BYTES));
        }
        boolean binary = false;
        for (int i = 0; i < Math.min(data.length, 8000); i++) if (data[i] == 0) { binary = true; break; }
        String text = binary ? "" : new String(data, StandardCharsets.UTF_8);
        int lines = binary ? 0 : (int) text.chars().filter(c -> c == '\n').count() + 1;
        return ApiResponse.success("File", new FileContent(rel, CodeFiles.language(rel), text, size, lines, binary, size > MAX_VIEW_BYTES,
                info == null ? null : info.summary(), info == null ? null : info.layer(), null));
    }

    private Path safe(Path root, String rel) {
        if (rel == null || rel.isBlank()) throw new IllegalArgumentException("path is required");
        Path p = root.resolve(rel).normalize();
        if (!p.startsWith(root) || !Files.isRegularFile(p)) throw new NotFoundException("File not found");
        for (Path part : root.relativize(p)) {
            if (part.toString().equals(".git")) throw new NotFoundException("File not found");
        }
        return p;
    }

    @GetMapping("/{id}/semantic")
    public ApiResponse<SemanticTokenService.Tokens> semantic(@AuthenticationPrincipal UserDetails u, @PathVariable long id,
                                                             @RequestParam("path") String rel) throws IOException {
        ProjectAccess.Ctx ctx = access.ready(u.getUsername(), id);
        if (!rel.endsWith(".java")) return ApiResponse.success("No tokens", new SemanticTokenService.Tokens(SemanticTokenService.KINDS, List.of(), List.of()));
        Path p = safe(ctx.root(), rel);
        return ApiResponse.success("Tokens", new SemanticTokenService(ctx.k()).tokens(p, rel));
    }

    @GetMapping("/{id}/explain")
    public ApiResponse<Explanation> explain(@AuthenticationPrincipal UserDetails u, @PathVariable long id,
                                            @RequestParam(value = "kind", required = false) String kind,
                                            @RequestParam(value = "id", required = false) String refId,
                                            @RequestParam(value = "ref", required = false) String ref,
                                            @RequestParam(value = "file", required = false) String file,
                                            @RequestParam(value = "line", defaultValue = "0") int line,
                                            @RequestParam(value = "word", required = false) String word) {
        Knowledge k = access.ready(u.getUsername(), id).k();
        Explanation e = null;
        if (ref != null) e = ExplainResolver.byRef(k, ref);
        if (e == null && kind != null) e = ExplainResolver.byId(k, kind, refId);
        if (e == null && file != null) e = line > 0 ? ExplainResolver.at(k, file, line, word) : ExplainResolver.byId(k, "file", file);
        if (e == null) throw new NotFoundException("Nothing to explain here");
        return ApiResponse.success("Explanation", e);
    }

    public record SymbolHit(String kind, String name, String detail, String file, int line, String id) {}

    @GetMapping("/{id}/symbols")
    public ApiResponse<List<SymbolHit>> symbols(@AuthenticationPrincipal UserDetails u, @PathVariable long id, @RequestParam("q") String q) {
        Knowledge k = access.ready(u.getUsername(), id).k();
        String s = q.trim().toLowerCase(Locale.ROOT);
        List<SymbolHit> out = new ArrayList<>();
        if (s.length() < 2) return ApiResponse.success("Symbols", out);
        for (ClassInfo c : k.result.classes()) {
            if (c.test() || c.nested()) continue;
            if (c.name().toLowerCase(Locale.ROOT).contains(s)) out.add(new SymbolHit("class", c.name(), c.layer().toLowerCase(Locale.ROOT) + " · " + c.module(), c.file(), c.line(), c.id()));
            for (MethodInfo m : c.methods()) {
                if (!m.constructor() && m.name().toLowerCase(Locale.ROOT).contains(s) && out.size() < 60) {
                    out.add(new SymbolHit("method", c.name() + "." + m.name() + "()", m.signature(), c.file(), m.line(), m.id()));
                }
            }
        }
        for (EndpointInfo e : k.result.endpoints()) {
            if ((e.httpMethod() + " " + e.path()).toLowerCase(Locale.ROOT).contains(s)) out.add(new SymbolHit("endpoint", e.httpMethod() + " " + e.path(), e.fullPath(), e.file(), e.line(), e.id()));
        }
        for (FileInfo f : k.result.files()) {
            if (f.path().toLowerCase(Locale.ROOT).contains(s) && out.size() < 80) out.add(new SymbolHit("file", f.path().substring(f.path().lastIndexOf('/') + 1), f.path(), f.path(), 1, f.path()));
        }
        List<SymbolHit> trimmed = out.stream().sorted(Comparator.comparingInt((SymbolHit h) -> h.name().toLowerCase(Locale.ROOT).startsWith(s) ? 0 : 1)
                .thenComparing(SymbolHit::name)).limit(40).collect(Collectors.toList());
        return ApiResponse.success("Symbols", trimmed);
    }
}
