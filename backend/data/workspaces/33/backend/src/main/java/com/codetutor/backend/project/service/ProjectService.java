package com.codetutor.backend.project.service;

import com.codetutor.backend.analysis.AnalysisStore;
import com.codetutor.backend.auth.model.User;
import com.codetutor.backend.auth.repository.UserRepository;
import com.codetutor.backend.chat.ChatMessageRepository;
import com.codetutor.backend.common.exception.LimitExceededException;
import com.codetutor.backend.project.dto.CreateGithubProjectRequest;
import com.codetutor.backend.project.dto.ProjectResponse;
import com.codetutor.backend.project.model.Project;
import com.codetutor.backend.project.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Creates, lists, re-analyses and deletes projects — always scoped to the calling user. */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projects;
    private final UserRepository users;
    private final GitHubValidator gitHubValidator;
    private final ZipValidator zipValidator;
    private final ProjectPipelineService pipeline;
    private final WorkspaceManagerService workspaces;
    private final AnalysisStore store;
    private final ChatMessageRepository chats;
    private final ProjectAccess access;
    private final int maxProjects;
    private final Path uploadDir;

    public ProjectService(ProjectRepository projects, UserRepository users, GitHubValidator gitHubValidator, ZipValidator zipValidator,
                          ProjectPipelineService pipeline, WorkspaceManagerService workspaces, AnalysisStore store,
                          ChatMessageRepository chats, ProjectAccess access,
                          @Value("${limits.max-projects-per-user:10}") int maxProjects,
                          @Value("${app.data-dir:./data}") String dataDir) throws IOException {
        this.projects = projects;
        this.users = users;
        this.gitHubValidator = gitHubValidator;
        this.zipValidator = zipValidator;
        this.pipeline = pipeline;
        this.workspaces = workspaces;
        this.store = store;
        this.chats = chats;
        this.access = access;
        this.maxProjects = maxProjects;
        this.uploadDir = Paths.get(dataDir).toAbsolutePath().normalize().resolve("uploads");
        Files.createDirectories(uploadDir);
    }

    // Note: these create methods are deliberately not @Transactional — the row must be committed before the
    // background worker (another thread) looks it up.

    public ProjectResponse createFromGithub(String email, CreateGithubProjectRequest req) {
        User owner = access.user(email);
        checkQuota(owner, 0);
        GitHubValidator.GitHubRepoInfo info = gitHubValidator.validateAndExtract(req.getGithubUrl());
        Project p = Project.builder().name(req.getName().trim()).description(req.getDescription())
                .sourceType(Project.SourceType.GITHUB).githubUrl(info.canonicalUrl()).repositoryName(info.repositoryName())
                .owner(owner).build();
        p.setStatus(Project.AnalysisStatus.QUEUED);
        p.setStatusMessage("Waiting to start…");
        p = projects.save(p);
        log.info("GitHub project {} created by {}", p.getId(), email);
        pipeline.importGithub(p.getId(), info.cloneUrl());
        return ProjectResponse.from(p);
    }

    public ProjectResponse createFromZip(String email, String name, String description, MultipartFile file) {
        User owner = access.user(email);
        zipValidator.validate(file);
        checkQuota(owner, file.getSize());
        Project p = Project.builder().name(name.trim()).description(description).sourceType(Project.SourceType.ZIP)
                .zipFileName(file.getOriginalFilename()).zipFileSize(file.getSize()).owner(owner).build();
        p.setStatus(Project.AnalysisStatus.QUEUED);
        p.setStatusMessage("Waiting to start…");
        p = projects.save(p);
        Path tmp = uploadDir.resolve(p.getId() + ".zip");
        try {
            file.transferTo(tmp);
        } catch (IOException e) {
            projects.delete(p);
            throw new IllegalStateException("Could not store the uploaded file: " + e.getMessage());
        }
        log.info("ZIP project {} created by {} ({} bytes)", p.getId(), email, file.getSize());
        pipeline.importZip(p.getId(), tmp);
        return ProjectResponse.from(p);
    }

    public List<ProjectResponse> list(String email) {
        return projects.findByOwnerEmailOrderByCreatedAtDesc(email).stream().map(ProjectResponse::from).toList();
    }

    public ProjectResponse get(String email, long id) {
        return ProjectResponse.from(access.owned(email, id));
    }

    public void delete(String email, long id) {
        Project p = access.owned(email, id);
        Long ownerId = p.getOwner().getId();
        projects.delete(p);
        workspaces.deleteWorkspace(id);
        store.delete(id);
        chats.deleteByProjectId(id);
        try {
            Files.deleteIfExists(uploadDir.resolve(id + ".zip"));
        } catch (IOException ignored) {
            // leftover temp upload is harmless
        }
        pipeline.recomputeStorage(ownerId);
        log.info("Project {} deleted by {}", id, email);
    }

    public ProjectResponse reanalyze(String email, long id) {
        Project p = access.owned(email, id);
        if (p.getStatus() == Project.AnalysisStatus.CLONING || p.getStatus() == Project.AnalysisStatus.ANALYZING
                || p.getStatus() == Project.AnalysisStatus.EXTRACTING || p.getStatus() == Project.AnalysisStatus.QUEUED) {
            return ProjectResponse.from(p);
        }
        if (!workspaces.exists(id)) throw new IllegalArgumentException("The source files are no longer available — please import the project again.");
        p.setStatus(Project.AnalysisStatus.QUEUED);
        p.setProgressPercent(0);
        p.setStatusMessage("Waiting to start…");
        p = projects.save(p);
        store.evict(id);
        pipeline.reanalyze(id);
        return ProjectResponse.from(p);
    }

    /** Owner explicitly allows reading some sensitive files (.env, keys…) — the project is then re-analysed. */
    public ProjectResponse allowSensitive(String email, long id, List<String> paths) {
        Project p = access.owned(email, id);
        Set<String> merged = new LinkedHashSet<>();
        if (p.getAllowedSensitive() != null) merged.addAll(Arrays.asList(p.getAllowedSensitive().split("\n")));
        for (String s : paths) if (s != null && !s.isBlank() && !s.contains("..")) merged.add(s.trim());
        p.setAllowedSensitive(String.join("\n", merged));
        projects.save(p);
        return reanalyze(email, id);
    }

    private void checkQuota(User owner, long incoming) {
        if (projects.countByOwnerEmail(owner.getEmail()) >= maxProjects) {
            throw new LimitExceededException("Project limit reached — you can keep up to " + maxProjects
                    + " projects. Delete one to add another.");
        }
        long used = projects.sumSizeByOwner(owner.getId());
        if (used + incoming > owner.getStorageLimitBytes()) {
            throw new LimitExceededException("Your storage quota is full. Delete a project to free space.");
        }
    }
}
