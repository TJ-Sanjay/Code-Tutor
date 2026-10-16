package com.codetutor.backend.project.service;

import com.codetutor.backend.project.model.Project;
import com.codetutor.backend.project.model.Project.AnalysisStatus;
import com.codetutor.backend.project.repository.ProjectRepository;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.codetutor.backend.detector.service.TechnologyDetectorService;
import com.codetutor.backend.parser.service.JavaParserService;
import com.codetutor.backend.scanner.service.FileScannerService;

/**
 * Module 6 — Repository Extraction Service.
 *
 * Responsible for physical repository extraction into isolated workspace directories:
 *   1. GitHub Repositories: Uses JGit to clone public repositories into /workspaces/{projectId}
 *   2. ZIP Archives: Extracts uploaded .zip files safely into /workspaces/{projectId}
 */
@Service
public class RepositoryExtractorService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryExtractorService.class);

    private final ProjectRepository projectRepository;
    private final WorkspaceManagerService workspaceManagerService;
    private final FileScannerService fileScannerService;
    private final TechnologyDetectorService technologyDetectorService;
    private final JavaParserService javaParserService;

    public RepositoryExtractorService(
            ProjectRepository projectRepository,
            WorkspaceManagerService workspaceManagerService,
            FileScannerService fileScannerService,
            TechnologyDetectorService technologyDetectorService,
            JavaParserService javaParserService
    ) {
        this.projectRepository = projectRepository;
        this.workspaceManagerService = workspaceManagerService;
        this.fileScannerService = fileScannerService;
        this.technologyDetectorService = technologyDetectorService;
        this.javaParserService = javaParserService;
    }

    /**
     * Gets the workspace path for a specific project.
     */
    public Path getWorkspacePath(Long projectId) {
        return workspaceManagerService.getWorkspaceDir(projectId);
    }

    /**
     * Clones a GitHub repository into the project's workspace directory.
     *
     * @param project the target project
     * @param cloneUrl the git clone URL
     */
    @Transactional
    public void extractGithubRepo(Project project, String cloneUrl) {
        Path targetDir = getWorkspacePath(project.getId());
        log.info("Starting GitHub clone for project {} (id={}) into {}", project.getName(), project.getId(), targetDir);

        updateStatus(project, AnalysisStatus.CLONING, 25, "Cloning GitHub repository: " + project.getRepositoryName());

        try {
            // Prepare clean workspace directory via WorkspaceManagerService
            workspaceManagerService.createOrCleanWorkspace(project.getId());

            // Clone repository using JGit
            try (Git git = Git.cloneRepository()
                    .setURI(cloneUrl)
                    .setDirectory(targetDir.toFile())
                    .setCloneAllBranches(false)
                    .setDepth(1) // Shallow clone for speed & efficiency
                    .call()) {
                log.info("Successfully cloned GitHub repository for project id={}", project.getId());
            }

            // Module 8: Scan workspace files and update file count
            fileScannerService.scanWorkspace(project.getId());

            // Module 10: Detect technology stack
            technologyDetectorService.detectTechStack(project.getId());

            // Module 11: Parse Java source files into ASTs
            javaParserService.parseWorkspaceJavaFiles(project.getId());

            updateStatus(project, AnalysisStatus.COMPLETED, 100, "Repository cloned, scanned, and Java AST parsed successfully");

        } catch (Exception e) {
            log.error("Failed to clone GitHub repository for project id={}: {}", project.getId(), e.getMessage(), e);
            updateStatus(project, AnalysisStatus.FAILED, 0, "Clone failed: " + e.getMessage());
            throw new RuntimeException("GitHub clone failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts an uploaded ZIP archive file into the project's workspace directory.
     *
     * @param project the target project
     * @param file the uploaded multipart zip file
     */
    @Transactional
    public void extractZipArchive(Project project, MultipartFile file) {
        Path targetDir = getWorkspacePath(project.getId());
        log.info("Starting ZIP extraction for project {} (id={}) into {}", project.getName(), project.getId(), targetDir);

        updateStatus(project, AnalysisStatus.EXTRACTING, 25, "Extracting ZIP archive: " + file.getOriginalFilename());

        try {
            // Prepare clean workspace directory via WorkspaceManagerService
            workspaceManagerService.createOrCleanWorkspace(project.getId());

            // Extract ZIP stream safely
            try (InputStream is = file.getInputStream();
                 ZipInputStream zis = new ZipInputStream(is)) {

                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path entryPath = targetDir.resolve(entry.getName()).normalize();

                    // Security check against Zip Slip vulnerability
                    if (!entryPath.startsWith(targetDir)) {
                        throw new SecurityException("Bad zip entry path (Zip Slip attack attempt): " + entry.getName());
                    }

                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        // Ensure parent directory exists
                        if (entryPath.getParent() != null && !Files.exists(entryPath.getParent())) {
                            Files.createDirectories(entryPath.getParent());
                        }

                        // Write entry content
                        try (OutputStream os = Files.newOutputStream(entryPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                            zis.transferTo(os);
                        }
                    }
                    zis.closeEntry();
                }
            }

            log.info("Successfully extracted ZIP archive for project id={}", project.getId());

            // Module 8: Scan workspace files and update file count
            fileScannerService.scanWorkspace(project.getId());

            // Module 10: Detect technology stack
            technologyDetectorService.detectTechStack(project.getId());

            // Module 11: Parse Java source files into ASTs
            javaParserService.parseWorkspaceJavaFiles(project.getId());

            updateStatus(project, AnalysisStatus.COMPLETED, 100, "ZIP archive extracted, scanned, and Java AST parsed successfully");

        } catch (Exception e) {
            log.error("Failed to extract ZIP archive for project id={}: {}", project.getId(), e.getMessage(), e);
            updateStatus(project, AnalysisStatus.FAILED, 0, "Extraction failed: " + e.getMessage());
            throw new RuntimeException("ZIP extraction failed: " + e.getMessage(), e);
        }
    }

    private void updateStatus(Project project, AnalysisStatus status, int progress, String message) {
        project.setStatus(status);
        project.setProgressPercent(progress);
        project.setStatusMessage(message);
        projectRepository.save(project);
    }
}
