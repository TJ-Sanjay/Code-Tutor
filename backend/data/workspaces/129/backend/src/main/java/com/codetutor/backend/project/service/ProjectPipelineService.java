package com.codetutor.backend.project.service;

import com.codetutor.backend.analysis.AnalysisPipeline;
import com.codetutor.backend.analysis.AnalysisStore;
import com.codetutor.backend.analysis.model.AnalysisModel.Result;
import com.codetutor.backend.auth.model.User;
import com.codetutor.backend.auth.repository.UserRepository;
import com.codetutor.backend.common.exception.LimitExceededException;
import com.codetutor.backend.project.model.Project;
import com.codetutor.backend.project.model.Project.AnalysisStatus;
import com.codetutor.backend.project.repository.ProjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Background pipeline for one project: fetch the code (clone / extract, with limits) → analyse → persist.
 * Runs on a bounded thread pool so users can keep working while a repository is being processed.
 */
@Service
public class ProjectPipelineService {

    private static final Logger log = LoggerFactory.getLogger(ProjectPipelineService.class);

    private final ProjectRepository projects;
    private final UserRepository users;
    private final WorkspaceManagerService workspaces;
    private final AnalysisPipeline pipeline;
    private final AnalysisStore store;
    private final ObjectMapper mapper;
    private final long maxZipEntries;
    private final long maxRepoBytes;
    private final long maxFiles;

    public ProjectPipelineService(ProjectRepository projects, UserRepository users, WorkspaceManagerService workspaces,
                                  AnalysisPipeline pipeline, AnalysisStore store, ObjectMapper mapper,
                                  @Value("${limits.max-zip-entries:20000}") long maxZipEntries,
                                  @Value("${limits.max-repo-bytes:209715200}") long maxRepoBytes,
                                  @Value("${limits.max-files:8000}") long maxFiles) {
        this.projects = projects;
        this.users = users;
        this.workspaces = workspaces;
        this.pipeline = pipeline;
        this.store = store;
        this.mapper = mapper;
        this.maxZipEntries = maxZipEntries;
        this.maxRepoBytes = maxRepoBytes;
        this.maxFiles = maxFiles;
    }

    // ───────────────────────── entry points ─────────────────────────

    @Async("analysisExecutor")
    public void importGithub(long projectId, String cloneUrl) {
        run(projectId, () -> {
            update(projectId, AnalysisStatus.CLONING, 5, "Cloning repository from GitHub…");
            Path dir = workspaces.createOrCleanWorkspace(projectId);
            try (Git ignored = Git.cloneRepository().setURI(cloneUrl).setDirectory(dir.toFile()).setDepth(1)
                    .setCloneAllBranches(false).setTimeout(180).call()) {
                log.info("Cloned {} for project {}", cloneUrl, projectId);
            }
            workspaces.deleteTree(dir.resolve(".git")); // history is not needed for analysis
        });
    }

    @Async("analysisExecutor")
    public void importZip(long projectId, Path zipFile) {
        run(projectId, () -> {
            update(projectId, AnalysisStatus.EXTRACTING, 5, "Extracting uploaded archive…");
            Path dir = workspaces.createOrCleanWorkspace(projectId);
            extractZip(zipFile, dir);
            flattenSingleRoot(dir);
        });
        try {
            Files.deleteIfExists(zipFile);
        } catch (IOException ignored) {
            // temp upload cleanup is best effort
        }
    }

    @Async("analysisExecutor")
    public void reanalyze(long projectId) {
        run(projectId, () -> update(projectId, AnalysisStatus.QUEUED, 5, "Preparing re-analysis…"));
    }

    // ───────────────────────── core ─────────────────────────

    private interface Fetch { void run() throws Exception; }

    private void run(long projectId, Fetch fetch) {
        try {
            fetch.run();
            Project p = projects.findById(projectId).orElse(null);
            if (p == null) return; // deleted while importing
            Path root = workspaces.getWorkspaceDir(projectId);
            if (!Files.isDirectory(root)) throw new IllegalStateException("The repository could not be downloaded.");

            update(projectId, AnalysisStatus.ANALYZING, 10, "Analysing the code…");
            Set<String> allowed = new HashSet<>();
            if (p.getAllowedSensitive() != null && !p.getAllowedSensitive().isBlank()) {
                allowed.addAll(Arrays.asList(p.getAllowedSensitive().split("\n")));
            }
            Result result = pipeline.analyze(root, p.getName(), allowed,
                    (pct, msg) -> update(projectId, AnalysisStatus.ANALYZING, pct, msg + "…"));
            store.save(projectId, result);
            finish(projectId, result, root);
        } catch (LimitExceededException e) {
            fail(projectId, e.getMessage(), true);
        } catch (Exception e) {
            log.error("Project {} failed: {}", projectId, e.getMessage(), e);
            fail(projectId, friendly(e), false);
        }
    }

    private void finish(long projectId, Result r, Path root) throws IOException {
        Project p = projects.findById(projectId).orElse(null);
        if (p == null) return;
        long size = workspaces.getWorkspaceSizeBytes(projectId);
        p.setSizeBytes(size);
        p.setFileCount(r.overview().totalFiles());
        p.setClassCount(r.overview().classCount());
        p.setEndpointCount(r.overview().endpointCount());
        p.setTableCount(r.overview().tableCount());
        p.setModuleCount(r.overview().moduleCount());
        p.setPrimaryLanguage(r.overview().languages().isEmpty() ? "Unknown" : r.overview().languages().keySet().iterator().next());
        p.setTechStackJson(mapper.writeValueAsString(r.overview().languages()));
        p.setStatus(AnalysisStatus.COMPLETED);
        p.setProgressPercent(100);
        p.setStatusMessage("Analysis complete");
        projects.save(p);
        recomputeStorage(p.getOwner().getId());
    }

    private void fail(long projectId, String message, boolean cleanup) {
        Project p = projects.findById(projectId).orElse(null);
        if (p == null) return;
        p.setStatus(AnalysisStatus.FAILED);
        p.setProgressPercent(0);
        p.setStatusMessage(message.length() > 480 ? message.substring(0, 477) + "…" : message);
        projects.save(p);
        if (cleanup) workspaces.deleteWorkspace(projectId);
    }

    private void update(long projectId, AnalysisStatus status, int pct, String message) {
        projects.findById(projectId).ifPresent(p -> {
            p.setStatus(status);
            p.setProgressPercent(pct);
            p.setStatusMessage(message);
            projects.save(p);
        });
    }

    public void recomputeStorage(Long userId) {
        User u = users.findById(userId).orElse(null);
        if (u == null) return;
        u.setStorageUsedBytes(projects.sumSizeByOwner(userId));
        users.save(u);
    }

    private static String friendly(Exception e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (m.contains("not authorized") || m.contains("Authentication is required")) {
            return "GitHub refused access — the repository must be public.";
        }
        if (m.contains("UnknownHost") || m.contains("Connection") || m.contains("timed out")) {
            return "Could not reach GitHub. Check your connection and try again.";
        }
        return "Analysis failed: " + m;
    }

    // ───────────────────────── zip handling ─────────────────────────

    private void extractZip(Path zip, Path target) throws IOException {
        long total = 0;
        long entries = 0;
        try (InputStream is = Files.newInputStream(zip); ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            byte[] buf = new byte[16 * 1024];
            while ((entry = zis.getNextEntry()) != null) {
                if (++entries > maxZipEntries) {
                    throw new LimitExceededException("The archive contains too many entries (limit " + maxZipEntries + ").");
                }
                Path out = target.resolve(entry.getName()).normalize();
                if (!out.startsWith(target)) {
                    throw new LimitExceededException("The archive contains an unsafe path and was rejected.");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                if (ignoredZipPath(entry.getName())) { zis.closeEntry(); continue; } // skip node_modules, .git, … early
                Files.createDirectories(out.getParent());
                try (OutputStream os = Files.newOutputStream(out)) {
                    int n;
                    while ((n = zis.read(buf)) > 0) {
                        total += n;
                        if (total > maxRepoBytes) {
                            throw new LimitExceededException("The archive expands to more than " + (maxRepoBytes / 1024 / 1024)
                                    + " MB. Please upload a smaller project.");
                        }
                        os.write(buf, 0, n);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static boolean ignoredZipPath(String name) {
        String n = "/" + name.replace('\\', '/');
        for (String d : List.of("/node_modules/", "/.git/", "/target/", "/__pycache__/", "/.idea/", "/.gradle/", "/.venv/", "/dist/", "/build/")) {
            if (n.contains(d)) return true;
        }
        return false;
    }

    /** GitHub-style archives wrap everything in one folder; move its content up so paths look like the repo. */
    private void flattenSingleRoot(Path dir) throws IOException {
        List<Path> children;
        try (Stream<Path> s = Files.list(dir)) { children = s.toList(); }
        if (children.size() != 1 || !Files.isDirectory(children.get(0))) return;
        Path only = children.get(0);
        List<Path> inner;
        try (Stream<Path> s = Files.list(only)) { inner = s.toList(); }
        for (Path p : inner) Files.move(p, dir.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(only);
    }
}
