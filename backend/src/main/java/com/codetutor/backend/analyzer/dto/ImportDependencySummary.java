package com.codetutor.backend.analyzer.dto;

import java.util.List;
import java.util.Map;

public class ImportDependencySummary {

    private Long projectId;
    private int totalFilesAnalyzed;
    private int totalInternalDependencies;
    private int totalExternalDependencies;
    
    // Maps a file/class name to a list of internal classes it imports
    private Map<String, List<String>> internalDependencies;
    
    // Maps a file/class name to a list of external packages/classes it imports
    private Map<String, List<String>> externalDependencies;

    public ImportDependencySummary() {
    }

    public ImportDependencySummary(Long projectId, int totalFilesAnalyzed, int totalInternalDependencies, int totalExternalDependencies,
                                   Map<String, List<String>> internalDependencies, Map<String, List<String>> externalDependencies) {
        this.projectId = projectId;
        this.totalFilesAnalyzed = totalFilesAnalyzed;
        this.totalInternalDependencies = totalInternalDependencies;
        this.totalExternalDependencies = totalExternalDependencies;
        this.internalDependencies = internalDependencies;
        this.externalDependencies = externalDependencies;
    }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public int getTotalFilesAnalyzed() { return totalFilesAnalyzed; }
    public void setTotalFilesAnalyzed(int totalFilesAnalyzed) { this.totalFilesAnalyzed = totalFilesAnalyzed; }

    public int getTotalInternalDependencies() { return totalInternalDependencies; }
    public void setTotalInternalDependencies(int totalInternalDependencies) { this.totalInternalDependencies = totalInternalDependencies; }

    public int getTotalExternalDependencies() { return totalExternalDependencies; }
    public void setTotalExternalDependencies(int totalExternalDependencies) { this.totalExternalDependencies = totalExternalDependencies; }

    public Map<String, List<String>> getInternalDependencies() { return internalDependencies; }
    public void setInternalDependencies(Map<String, List<String>> internalDependencies) { this.internalDependencies = internalDependencies; }

    public Map<String, List<String>> getExternalDependencies() { return externalDependencies; }
    public void setExternalDependencies(Map<String, List<String>> externalDependencies) { this.externalDependencies = externalDependencies; }
}
