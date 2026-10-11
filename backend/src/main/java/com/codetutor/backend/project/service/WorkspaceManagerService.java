package com.codetutor.backend.project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Module 7 — Workspace Manager Service.
 *
 * Manages physical workspace folder lifecycle for project source code:
 *   1. Resolves and validates isolated project workspace directories (/workspaces/{projectId})
 *   2. Prevents path traversal vulnerabilities
 *   3. Handles workspace directory creation, existence verification, and population checks
 *   4. Calculates total disk space consumed by a project workspace
 *   5. Safely deletes project workspace directories when projects are deleted
 */
@Service
public class WorkspaceManagerService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceManagerService.class);

    private final Path workspaceRoot;

    public WorkspaceManagerService(
            @Value("${workspace.root-dir:./workspaces}") String workspaceRootDir
    ) {
        this.workspaceRoot = Paths.get(workspaceRootDir).toAbsolutePath().normalize();
        initWorkspaceRoot();
    }

    private void initWorkspaceRoot() {
        try {
            if (!Files.exists(workspaceRoot)) {
                Files.createDirectories(workspaceRoot);
                log.info("Workspace root directory created: {}", workspaceRoot);
            }
        } catch (IOException e) {
            log.error("Failed to initialize workspace root directory {}: {}", workspaceRoot, e.getMessage(), e);
        }
    }

    /**
     * Resolves the isolated workspace path for a given project ID.
     * Guarantees path remains strictly inside workspaceRoot (prevents path traversal).
     */
    public Path getWorkspaceDir(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID cannot be null");
        }
        Path projectPath = workspaceRoot.resolve(String.valueOf(projectId)).normalize();
        if (!projectPath.startsWith(workspaceRoot)) {
            throw new SecurityException("Access denied: Invalid workspace path traversal attempt for project ID " + projectId);
        }
        return projectPath;
    }

    /**
     * Prepares a clean workspace directory for a project (creates directory, removes old files if present).
     */
    public Path createOrCleanWorkspace(Long projectId) throws IOException {
        Path dir = getWorkspaceDir(projectId);
        if (Files.exists(dir)) {
            deleteWorkspace(projectId);
        }
        Files.createDirectories(dir);
        log.info("Created clean workspace directory for project id={}: {}", projectId, dir);
        return dir;
    }

    /**
     * Checks if a workspace directory exists for a project.
     */
    public boolean exists(Long projectId) {
        Path dir = getWorkspaceDir(projectId);
        return Files.exists(dir) && Files.isDirectory(dir);
    }

    /**
     * Checks if a project's workspace exists and contains at least one source file.
     */
    public boolean isPopulated(Long projectId) {
        if (!exists(projectId)) return false;
        Path dir = getWorkspaceDir(projectId);
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.anyMatch(p -> !p.equals(dir));
        } catch (IOException e) {
            log.warn("Error checking population of workspace for project id={}: {}", projectId, e.getMessage());
            return false;
        }
    }

    /**
     * Calculates the total storage size (in bytes) used by a project's workspace.
     */
    public long getWorkspaceSizeBytes(Long projectId) {
        if (!exists(projectId)) return 0L;
        Path dir = getWorkspaceDir(projectId);
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile)
                         .mapToLong(p -> {
                             try { return Files.size(p); }
                             catch (IOException e) { return 0L; }
                         })
                         .sum();
        } catch (IOException e) {
            log.warn("Could not compute workspace size for project id={}: {}", projectId, e.getMessage());
            return 0L;
        }
    }

    /**
     * Safely deletes a project's entire workspace directory from disk.
     */
    public boolean deleteWorkspace(Long projectId) {
        Path dir = getWorkspaceDir(projectId);
        if (!Files.exists(dir)) {
            log.debug("No workspace directory found to delete for project id={}", projectId);
            return true;
        }

        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                  .map(Path::toFile)
                  .forEach(File::delete);
            log.info("Successfully deleted workspace directory for project id={}", projectId);
            return true;
        } catch (IOException e) {
            log.error("Failed to delete workspace directory for project id={}: {}", projectId, e.getMessage(), e);
            return false;
        }
    }
}
