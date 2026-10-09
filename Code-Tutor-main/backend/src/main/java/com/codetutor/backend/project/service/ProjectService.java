package com.codetutor.backend.project.service;

import com.codetutor.backend.auth.model.User;
import com.codetutor.backend.auth.repository.UserRepository;
import com.codetutor.backend.project.dto.CreateGithubProjectRequest;
import com.codetutor.backend.project.dto.ProjectResponse;
import com.codetutor.backend.project.model.Project;
import com.codetutor.backend.project.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Project service — handles creating and managing projects.
 *
 * Module 3: Project CRUD + status management
 * Module 4: GitHub URL validation via GitHubValidator
 * Module 5: ZIP upload validation via ZipValidator
 */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    // Max projects per user
    private static final int MAX_PROJECTS_PER_USER = 10;

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final GitHubValidator gitHubValidator;
    private final ZipValidator zipValidator;
    private final RepositoryExtractorService repositoryExtractorService;
    private final WorkspaceManagerService workspaceManagerService;

    public ProjectService(
            ProjectRepository projectRepository,
            UserRepository userRepository,
            GitHubValidator gitHubValidator,
            ZipValidator zipValidator,
            RepositoryExtractorService repositoryExtractorService,
            WorkspaceManagerService workspaceManagerService
    ) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.gitHubValidator = gitHubValidator;
        this.zipValidator = zipValidator;
        this.repositoryExtractorService = repositoryExtractorService;
        this.workspaceManagerService = workspaceManagerService;
    }

    // ─── Module 3+4+6: Create project from GitHub URL and Extract ───────────────────────

    @Transactional
    public ProjectResponse createFromGithub(String ownerEmail, CreateGithubProjectRequest request) {
        User owner = loadUser(ownerEmail);
        checkProjectLimit(ownerEmail);

        // Module 4: Validate GitHub URL
        GitHubValidator.GitHubRepoInfo repoInfo = gitHubValidator.validateAndExtract(request.getGithubUrl());

        Project project = Project.builder()
                .name(request.getName())
                .description(request.getDescription())
                .sourceType(Project.SourceType.GITHUB)
                .githubUrl(repoInfo.canonicalUrl())
                .repositoryName(repoInfo.repositoryName())
                .owner(owner)
                .build();

        project = projectRepository.save(project);
        log.info("GitHub project created: {} (id={}) for user: {}", project.getName(), project.getId(), ownerEmail);

        // Module 6: Extract GitHub repository into workspace
        repositoryExtractorService.extractGithubRepo(project, repoInfo.cloneUrl());

        return ProjectResponse.from(project);
    }

    // ─── Module 3+5+6: Create project from ZIP upload and Extract ───────────────────────

    @Transactional
    public ProjectResponse createFromZip(String ownerEmail, String projectName, String description, MultipartFile file) {
        User owner = loadUser(ownerEmail);
        checkProjectLimit(ownerEmail);

        // Module 5: Validate ZIP file
        zipValidator.validate(file);

        Project project = Project.builder()
                .name(projectName)
                .description(description)
                .sourceType(Project.SourceType.ZIP)
                .zipFileName(file.getOriginalFilename())
                .zipFileSize(file.getSize())
                .owner(owner)
                .build();

        project = projectRepository.save(project);
        log.info("ZIP project created: {} (id={}) for user: {}", project.getName(), project.getId(), ownerEmail);

        // Module 6: Extract ZIP archive into workspace
        repositoryExtractorService.extractZipArchive(project, file);

        return ProjectResponse.from(project);
    }

    // ─── Module 3: List all projects for a user ───────────────────────────

    @Transactional(readOnly = true)
    public List<ProjectResponse> listProjects(String ownerEmail) {
        return projectRepository
                .findByOwnerEmailOrderByCreatedAtDesc(ownerEmail)
                .stream()
                .map(ProjectResponse::from)
                .toList();
    }

    // ─── Module 3: Get a single project (owner-scoped) ────────────────────

    @Transactional(readOnly = true)
    public ProjectResponse getProject(String ownerEmail, Long projectId) {
        Project project = projectRepository
                .findByIdAndOwnerEmail(projectId, ownerEmail)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Project not found or access denied: " + projectId
                ));
        return ProjectResponse.from(project);
    }

    // ─── Module 3+7: Delete a project and its physical workspace ───────────────

    @Transactional
    public void deleteProject(String ownerEmail, Long projectId) {
        if (!projectRepository.existsByIdAndOwnerEmail(projectId, ownerEmail)) {
            throw new IllegalArgumentException(
                    "Project not found or access denied: " + projectId
            );
        }
        projectRepository.deleteById(projectId);
        workspaceManagerService.deleteWorkspace(projectId);
        log.info("Project deleted (DB record & workspace folder): id={} by user: {}", projectId, ownerEmail);
    }

    // ─── Module 3: Get analysis status (for polling) ──────────────────────

    @Transactional(readOnly = true)
    public ProjectResponse getAnalysisStatus(String ownerEmail, Long projectId) {
        return getProject(ownerEmail, projectId);
    }

    // ─── Internal helpers ─────────────────────────────────────────────────

    private User loadUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }

    private void checkProjectLimit(String ownerEmail) {
        long count = projectRepository.countByOwnerEmail(ownerEmail);
        if (count >= MAX_PROJECTS_PER_USER) {
            throw new IllegalArgumentException(
                    "Project limit reached. You can have at most " + MAX_PROJECTS_PER_USER +
                    " projects. Please delete an existing project first."
            );
        }
    }
}
