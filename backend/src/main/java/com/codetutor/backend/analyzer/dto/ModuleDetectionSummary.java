package com.codetutor.backend.analyzer.dto;

import java.util.List;

public class ModuleDetectionSummary {
    private Long projectId;
    private List<LogicalModule> modules;

    public ModuleDetectionSummary() {}

    public ModuleDetectionSummary(Long projectId, List<LogicalModule> modules) {
        this.projectId = projectId;
        this.modules = modules;
    }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public List<LogicalModule> getModules() { return modules; }
    public void setModules(List<LogicalModule> modules) { this.modules = modules; }
}
