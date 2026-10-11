package com.codetutor.backend.analyzer.dto;

import java.util.List;

public class DatabaseSchemaSummary {
    private Long projectId;
    private List<DatabaseTable> tables;

    public DatabaseSchemaSummary() {}

    public DatabaseSchemaSummary(Long projectId, List<DatabaseTable> tables) {
        this.projectId = projectId;
        this.tables = tables;
    }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public List<DatabaseTable> getTables() { return tables; }
    public void setTables(List<DatabaseTable> tables) { this.tables = tables; }
}
