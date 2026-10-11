package com.codetutor.backend.scanner.service;

import com.codetutor.backend.project.model.Project;
import com.codetutor.backend.project.repository.ProjectRepository;
import com.codetutor.backend.project.service.WorkspaceManagerService;
import com.codetutor.backend.scanner.dto.FileNode;
import com.codetutor.backend.scanner.dto.ScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Module 8 — File Scanner Service.
 *
 * Recursively scans project workspace directories to:
 *   1. Build a hierarchical FileNode tree of files and directories
 *   2. Collect workspace metrics (total file count, total directory count, total size)
 *   3. Update project metadata in database (fileCount)
 *   4. Provide flat list of file paths for parser modules
 */
@Service
public class FileScannerService {

    private static final Logger log = LoggerFactory.getLogger(FileScannerService.class);

    private final WorkspaceManagerService workspaceManagerService;
    private final ProjectRepository projectRepository;
    private final FileFilterService fileFilterService;

    public FileScannerService(
            WorkspaceManagerService workspaceManagerService,
            ProjectRepository projectRepository,
            FileFilterService fileFilterService
    ) {
        this.workspaceManagerService = workspaceManagerService;
        this.projectRepository = projectRepository;
        this.fileFilterService = fileFilterService;
    }

    /**
     * Recursively scans the project workspace and returns a complete ScanResult tree.
     */
    @Transactional
    public ScanResult scanWorkspace(Long projectId) {
        Path workspaceDir = workspaceManagerService.getWorkspaceDir(projectId);
        if (!Files.exists(workspaceDir) || !Files.isDirectory(workspaceDir)) {
            throw climateScanException("Workspace directory does not exist for project id=" + projectId);
        }

        log.info("Starting file scan for project workspace id={}: {}", projectId, workspaceDir);

        int[] metrics = new int[]{0, 0}; // [0] = file count, [1] = dir count
        long[] totalSize = new long[]{0L};

        FileNode rootNode = buildNodeRecursively(workspaceDir, workspaceDir, metrics, totalSize);

        ScanResult result = new ScanResult(
                projectId,
                metrics[0],
                metrics[1],
                totalSize[0],
                rootNode
        );

        // Update Project fileCount in database
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            project.setFileCount(metrics[0]);
            projectRepository.save(project);
            log.info("Project id={} updated with file count: {}", projectId, metrics[0]);
        }

        log.info("Scan completed for project id={}: {} files, {} dirs, {} bytes",
                projectId, metrics[0], metrics[1], totalSize[0]);

        return result;
    }

    /**
     * Returns a flat list of relative paths for all regular non-filtered source files in project workspace.
     */
    public List<String> getFlatFileList(Long projectId) {
        Path workspaceDir = workspaceManagerService.getWorkspaceDir(projectId);
        if (!Files.exists(workspaceDir)) return Collections.emptyList();

        List<String> files = new ArrayList<>();
        try (var stream = Files.walk(workspaceDir)) {
            stream.filter(p -> {
                boolean isDir = Files.isDirectory(p);
                return !p.equals(workspaceDir) && fileFilterService.shouldIncludePath(p, isDir);
            })
            .filter(Files::isRegularFile)
            .forEach(p -> {
                String relPath = workspaceDir.relativize(p).toString().replace('\\', '/');
                files.add(relPath);
            });
        } catch (IOException e) {
            log.error("Error reading file list for project id={}: {}", projectId, e.getMessage());
        }
        return files;
    }

    private FileNode buildNodeRecursively(Path rootPath, Path currentPath, int[] metrics, long[] totalSize) {
        String name = currentPath.equals(rootPath) ? currentPath.getFileName().toString() : currentPath.getFileName().toString();
        String relPath = rootPath.relativize(currentPath).toString().replace('\\', '/');
        if (relPath.isEmpty()) relPath = ".";

        boolean isDir = Files.isDirectory(currentPath);

        if (isDir) {
            metrics[1]++;
            FileNode node = new FileNode(name, relPath, "DIRECTORY", 0L, null);
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(currentPath)) {
                List<Path> childrenPaths = new ArrayList<>();
                for (Path p : ds) {
                    boolean pIsDir = Files.isDirectory(p);
                    // Filter out ignored directories (.git, node_modules, target) and ignored files (.env, .class)
                    if (fileFilterService.shouldIncludePath(p, pIsDir)) {
                        childrenPaths.add(p);
                    }
                }
                // Sort directories first, then files alphabetically
                childrenPaths.sort((a, b) -> {
                    boolean aIsDir = Files.isDirectory(a);
                    boolean bIsDir = Files.isDirectory(b);
                    if (aIsDir != bIsDir) return aIsDir ? -1 : 1;
                    return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
                });

                for (Path child : childrenPaths) {
                    FileNode childNode = buildNodeRecursively(rootPath, child, metrics, totalSize);
                    node.addChild(childNode);
                }
            } catch (IOException e) {
                log.warn("Could not read directory contents of {}: {}", currentPath, e.getMessage());
            }
            return node;
        } else {
            metrics[0]++;
            long size = 0L;
            try { size = Files.size(currentPath); } catch (IOException ignored) {}
            totalSize[0] += size;

            String ext = getFileExtension(name);
            return new FileNode(name, relPath, "FILE", size, ext);
        }
    }

    private String getFileExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx > 0 && idx < fileName.length() - 1) {
            return fileName.substring(idx + 1).toLowerCase();
        }
        return "";
    }

    private IllegalArgumentException climateScanException(String msg) {
        return new IllegalArgumentException(msg);
    }

    /**
     * Reads the raw text content of a specific file inside the project workspace.
     */
    public String getFileContent(Long projectId, String relativePath) {
        Path workspaceDir = workspaceManagerService.getWorkspaceDir(projectId);
        
        // Normalize and secure the path against directory traversal
        Path targetFile = workspaceDir.resolve(relativePath).normalize();
        if (!targetFile.startsWith(workspaceDir)) {
            throw new SecurityException("Invalid file path requested: " + relativePath);
        }
        
        if (!Files.exists(targetFile) || !Files.isRegularFile(targetFile)) {
            throw new IllegalArgumentException("File not found: " + relativePath);
        }

        try {
            return Files.readString(targetFile);
        } catch (IOException e) {
            log.error("Failed to read file content for {}", targetFile, e);
            throw new RuntimeException("Could not read file content: " + e.getMessage());
        }
    }
}
