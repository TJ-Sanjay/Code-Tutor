package com.codetutor.backend.scanner.dto;

/**
 * Summary of a project workspace file scan operation.
 */
public class ScanResult {

    private Long projectId;
    private int totalFiles;
    private int totalDirectories;
    private long totalSizeBytes;
    private FileNode rootNode;

    public ScanResult() {}

    public ScanResult(Long projectId, int totalFiles, int totalDirectories, long totalSizeBytes, FileNode rootNode) {
        this.projectId = projectId;
        this.totalFiles = totalFiles;
        this.totalDirectories = totalDirectories;
        this.totalSizeBytes = totalSizeBytes;
        this.rootNode = rootNode;
    }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public int getTotalFiles() { return totalFiles; }
    public void setTotalFiles(int totalFiles) { this.totalFiles = totalFiles; }

    public int getTotalDirectories() { return totalDirectories; }
    public void setTotalDirectories(int totalDirectories) { this.totalDirectories = totalDirectories; }

    public long getTotalSizeBytes() { return totalSizeBytes; }
    public void setTotalSizeBytes(long totalSizeBytes) { this.totalSizeBytes = totalSizeBytes; }

    public FileNode getRootNode() { return rootNode; }
    public void setRootNode(FileNode rootNode) { this.rootNode = rootNode; }
}
