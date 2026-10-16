package com.codetutor.backend.analysis;

import com.codetutor.backend.analysis.java.JavaAnalyzer;
import com.codetutor.backend.analysis.model.AnalysisModel.*;
import com.codetutor.backend.common.exception.LimitExceededException;
import com.codetutor.backend.explain.Describer;
import com.codetutor.backend.scanner.service.FileFilterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Runs the full analysis of an extracted repository:
 * scan → tech stack → Java AST → security → schema → front-end → modules → knowledge graph → chunks.
 */
@Service
public class AnalysisPipeline {

    private static final Logger log = LoggerFactory.getLogger(AnalysisPipeline.class);

    private static final Set<String> SENSITIVE_EXT = Set.of("pem", "key", "p12", "pfx", "jks", "keystore");

    private final FileFilterService filter;
    private final long maxFiles;
    private final long maxRepoBytes;
    private final long maxFileBytes;

    public AnalysisPipeline(FileFilterService filter,
                            @Value("${limits.max-files:8000}") long maxFiles,
                            @Value("${limits.max-repo-bytes:209715200}") long maxRepoBytes,
                            @Value("${limits.max-file-bytes:2097152}") long maxFileBytes) {
        this.filter = filter;
        this.maxFiles = maxFiles;
        this.maxRepoBytes = maxRepoBytes;
        this.maxFileBytes = maxFileBytes;
    }

    public Result analyze(Path root, String projectName, Set<String> allowedSensitive, BiConsumer<Integer, String> progress) {
        progress.accept(10, "Reading files and folders");
        List<String> sensitive = new ArrayList<>();
        List<FileInfo> files = scan(root, allowedSensitive, sensitive);

        progress.accept(25, "Detecting technology stack");
        TechStackAnalyzer.Result tech = new TechStackAnalyzer().analyze(root, files);
        String contextPath = tech.contextPath() == null ? "" : tech.contextPath();

        progress.accept(40, "Parsing source code");
        List<String> javaFiles = files.stream().map(FileInfo::path).filter(p -> p.endsWith(".java")).toList();
        JavaAnalyzer.Output java = new JavaAnalyzer().analyze(root, javaFiles, maxFileBytes, contextPath);

        progress.accept(55, "Analysing security, APIs and database");
        Security security = new SecurityAnalyzer().analyze(root, java.classes(), java.endpoints(), tech.libraries());
        List<EndpointInfo> endpoints = new ArrayList<>();
        for (EndpointInfo e : java.endpoints()) {
            String access = SecurityAnalyzer.accessFor(security, e);
            String note = preAuthorize(java.classes(), e);
            endpoints.add(new EndpointInfo(e.id(), e.httpMethod(), e.path(), e.fullPath(), e.controller(), e.handler(), e.file(),
                    e.line(), e.params(), e.returns(), access, note, null, e.viewResponse()));
        }
        List<String> sqlFiles = files.stream().map(FileInfo::path).filter(p -> p.toLowerCase(Locale.ROOT).endsWith(".sql")).toList();
        Schema schema = new SchemaAnalyzer().analyze(root, java.classes(), sqlFiles);

        progress.accept(65, "Analysing the front-end");
        List<String> jsFiles = files.stream().map(FileInfo::path).filter(AnalysisPipeline::isFrontendSource).toList();
        Frontend frontend = new FrontendAnalyzer().analyze(root, jsFiles, endpoints, contextPath);

        progress.accept(75, "Building the knowledge graph");
        // attach layer / module to files
        Map<String, ClassInfo> mainClassOfFile = new HashMap<>();
        Map<String, List<String>> classesOfFile = new HashMap<>();
        for (ClassInfo c : java.classes()) {
            classesOfFile.computeIfAbsent(c.file(), k -> new ArrayList<>()).add(c.id());
            if (!c.nested()) mainClassOfFile.putIfAbsent(c.file(), c);
        }
        List<FileInfo> enriched = new ArrayList<>();
        for (FileInfo f : files) {
            ClassInfo c = mainClassOfFile.get(f.path());
            enriched.add(new FileInfo(f.path(), f.language(), f.lines(), f.size(), c == null ? null : c.layer(),
                    c == null ? null : c.module(), null, classesOfFile.get(f.path())));
        }
        GraphBuilder gb = new GraphBuilder();
        List<ModuleInfo> modules = gb.modules(java.classes(), endpoints, schema, enriched);

        Overview overview0 = overview(projectName, enriched, tech, java, endpoints, schema, modules, security, frontend, sensitive);
        Result draft = new Result(overview0, enriched, java.classes(), endpoints, schema, modules, security, frontend,
                tech.libraries(), List.of(), List.of(), List.of());

        // summaries (need the derived indexes)
        progress.accept(85, "Writing explanations");
        Knowledge k0 = new Knowledge(draft);
        Describer d0 = new Describer(k0);
        List<ClassInfo> withSummary = new ArrayList<>();
        for (ClassInfo c : java.classes()) withSummary.add(c.withSummary(d0.classSummary(c)));
        Result r1 = new Result(overview0, enriched, withSummary, endpoints, schema, modules, security, frontend, tech.libraries(),
                List.of(), List.of(), List.of());
        Knowledge k1 = new Knowledge(r1);
        Describer d1 = new Describer(k1);
        List<FileInfo> summarised = new ArrayList<>();
        for (FileInfo f : enriched) {
            summarised.add(new FileInfo(f.path(), f.language(), f.lines(), f.size(), f.layer(), f.module(),
                    d1.fileSummary(root, f, tech.libraries()), f.classes()));
        }
        // endpoint descriptions
        List<EndpointInfo> described = new ArrayList<>();
        for (EndpointInfo e : endpoints) {
            ClassInfo c = k1.classById.get(e.controller());
            MethodInfo m = k1.methodById.get(e.handler());
            described.add(new EndpointInfo(e.id(), e.httpMethod(), e.path(), e.fullPath(), e.controller(), e.handler(), e.file(),
                    e.line(), e.params(), e.returns(), e.access(), e.accessNote(),
                    c != null && m != null ? d1.methodSummary(c, m) : null, e.viewResponse()));
        }

        progress.accept(92, "Building the semantic index");
        GraphBuilder.Graph graph = gb.graph(withSummary, described, schema, modules, frontend);
        Result r2 = new Result(overview0, summarised, withSummary, described, schema, modules, security, frontend, tech.libraries(),
                graph.nodes(), graph.edges(), List.of());
        Knowledge k2 = new Knowledge(r2);
        List<Chunk> chunks = new ChunkBuilder().build(root, k2, new Describer(k2), summarised);

        log.info("Analysis done: {} files, {} classes, {} endpoints, {} tables, {} chunks", summarised.size(), withSummary.size(),
                described.size(), schema.tables().size(), chunks.size());
        return new Result(overview0, summarised, withSummary, described, schema, modules, security, frontend, tech.libraries(),
                graph.nodes(), graph.edges(), chunks);
    }

    // ───────────────────────── scan ─────────────────────────

    private List<FileInfo> scan(Path root, Set<String> allowedSensitive, List<String> sensitive) {
        List<FileInfo> out = new ArrayList<>();
        long[] bytes = {0};
        try (Stream<Path> s = Files.walk(root)) {
            Iterator<Path> it = s.iterator();
            while (it.hasNext()) {
                Path p = it.next();
                if (p.equals(root)) continue;
                if (!insideAllowedDirs(root, p)) continue;
                if (!Files.isRegularFile(p)) continue;
                String rel = root.relativize(p).toString().replace('\\', '/');
                String name = p.getFileName().toString();
                boolean sens = isSensitive(name);
                if (sens && !allowedSensitive.contains(rel)) {
                    sensitive.add(rel);
                    continue;
                }
                if (!sens && filter.isIgnoredFile(name)) continue;
                long size = Files.size(p);
                bytes[0] += size;
                if (out.size() + 1 > maxFiles) {
                    throw new LimitExceededException("The repository has more than " + maxFiles + " source files. "
                            + "Please upload a smaller project (limit protects the server).");
                }
                if (bytes[0] > maxRepoBytes) {
                    throw new LimitExceededException("The repository is larger than " + (maxRepoBytes / 1024 / 1024)
                            + " MB of source. Please upload a smaller project.");
                }
                int lines = 0;
                if (size <= maxFileBytes && CodeFiles.textual(rel)) lines = countLines(p);
                out.add(new FileInfo(rel, CodeFiles.language(rel), lines, size, null, null, null, null));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the repository: " + e.getMessage(), e);
        }
        out.sort(Comparator.comparing(FileInfo::path));
        return out;
    }

    private boolean insideAllowedDirs(Path root, Path p) {
        Path rel = root.relativize(p);
        int n = rel.getNameCount() - (Files.isDirectory(p) ? 0 : 1);
        for (int i = 0; i < n; i++) {
            if (filter.isIgnoredDirectory(rel.getName(i).toString())) return false;
        }
        return true;
    }

    public static boolean isSensitive(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.equals(".env") || n.startsWith(".env.")) return !n.endsWith(".example") && !n.endsWith(".sample") && !n.endsWith(".template");
        int dot = n.lastIndexOf('.');
        if (dot > 0 && SENSITIVE_EXT.contains(n.substring(dot + 1))) return true;
        return n.startsWith("id_rsa") || n.startsWith("id_ed25519") || n.equals("secrets.yml") || n.equals("secrets.yaml")
                || n.equals("credentials.json") || n.equals("service-account.json");
    }

    private static int countLines(Path p) throws IOException {
        byte[] b = Files.readAllBytes(p);
        if (b.length == 0) return 0;
        int n = 1;
        for (byte x : b) if (x == '\n') n++;
        return n;
    }

    static boolean isFrontendSource(String p) {
        String l = p.toLowerCase(Locale.ROOT);
        return (l.endsWith(".js") || l.endsWith(".jsx") || l.endsWith(".ts") || l.endsWith(".tsx"))
                && !l.endsWith(".min.js") && !l.endsWith(".d.ts") && !l.contains("/test/") && !l.contains(".test.")
                && !l.contains(".spec.") && !l.endsWith("vite.config.js") && !l.endsWith("webpack.config.js")
                && !l.contains("/static/") && !l.contains("/webjars/");
    }

    private String preAuthorize(List<ClassInfo> classes, EndpointInfo e) {
        for (ClassInfo c : classes) {
            if (!c.id().equals(e.controller())) continue;
            for (MethodInfo m : c.methods()) {
                if (!m.id().equals(e.handler())) continue;
                for (Ann a : m.annotations()) {
                    if (Set.of("PreAuthorize", "Secured", "RolesAllowed").contains(a.name())) return a.render();
                }
            }
            for (Ann a : c.annotations()) {
                if (Set.of("PreAuthorize", "Secured", "RolesAllowed").contains(a.name())) return a.render();
            }
        }
        return null;
    }

    // ───────────────────────── overview ─────────────────────────

    private Overview overview(String name, List<FileInfo> files, TechStackAnalyzer.Result tech, JavaAnalyzer.Output java,
                              List<EndpointInfo> endpoints, Schema schema, List<ModuleInfo> modules, Security security,
                              Frontend frontend, List<String> sensitive) {
        List<ClassInfo> main = java.classes().stream().filter(c -> !c.test() && !c.nested()).toList();
        int methods = main.stream().mapToInt(c -> c.methods().size()).sum();
        int lines = files.stream().mapToInt(FileInfo::lines).sum();
        int sourceFiles = (int) files.stream().filter(f -> CodeFiles.isSource(f.path())).count();
        Map<String, Integer> layers = new LinkedHashMap<>();
        for (ClassInfo c : main) layers.merge(c.layer(), 1, Integer::sum);

        List<String> fw = tech.frameworks();
        StringBuilder sum = new StringBuilder();
        String primary = tech.languages().isEmpty() ? "multiple languages" : tech.languages().keySet().iterator().next();
        sum.append(name).append(" is ");
        if (!fw.isEmpty()) sum.append("a ").append(String.join(" + ", fw.subList(0, Math.min(3, fw.size())))).append(" application");
        else sum.append("a software project");
        sum.append(" written mostly in ").append(primary).append(". ");
        sum.append("It contains ").append(main.size()).append(" classes in ").append(modules.size()).append(" modules, ")
                .append(endpoints.size()).append(" REST/web endpoint").append(endpoints.size() == 1 ? "" : "s").append(" and ")
                .append(schema.tables().size()).append(" database table").append(schema.tables().size() == 1 ? "" : "s").append('.');
        if (frontend.present()) sum.append(" The ").append(frontend.framework()).append(" front-end has ").append(frontend.files().size()).append(" source files.");

        List<String> hi = new ArrayList<>();
        List<String> order = List.of("CONTROLLER", "SERVICE", "REPOSITORY", "ENTITY");
        List<String> present = order.stream().filter(layers::containsKey).toList();
        if (!present.isEmpty()) {
            hi.add("Architecture: " + present.stream().map(l -> l.toLowerCase(Locale.ROOT) + " (" + layers.get(l) + ")")
                    .collect(Collectors.joining(" → ")));
        }
        if (security.enabled()) hi.add("Security: " + security.summary());
        else hi.add("Security: no authentication/authorization configured.");
        if (!schema.tables().isEmpty()) {
            hi.add("Database: " + schema.tables().stream().map(Table::name).limit(8).collect(Collectors.joining(", "))
                    + (schema.tables().size() > 8 ? ", …" : ""));
        }
        main.stream().filter(c -> "APPLICATION".equals(c.layer())).findFirst()
                .ifPresent(c -> hi.add("Entry point: " + c.name() + " (" + c.file() + ")"));
        modules.stream().max(Comparator.comparingInt(m -> m.classes().size()))
                .ifPresent(m -> hi.add("Largest module: “" + m.name() + "” with " + m.classes().size() + " classes"));
        if (!sensitive.isEmpty()) hi.add("Skipped " + sensitive.size() + " sensitive file(s) (" + String.join(", ", sensitive.stream().limit(3).toList())
                + ") — not read for your safety.");

        return new Overview(name, Instant.now().toString(), files.size(), sourceFiles, lines, main.size(), methods,
                endpoints.size(), schema.tables().size(), modules.size(), java.basePackage(), tech.languages(), tech.frameworks(),
                tech.buildTools(), tech.databases(), tech.tools(), layers, sensitive, sum.toString(), hi);
    }
}
