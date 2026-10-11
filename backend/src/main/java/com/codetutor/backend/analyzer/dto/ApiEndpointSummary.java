package com.codetutor.backend.analyzer.dto;

import java.util.List;

public class ApiEndpointSummary {
    private Long projectId;
    private List<ApiEndpoint> endpoints;

    public ApiEndpointSummary() {}

    public ApiEndpointSummary(Long projectId, List<ApiEndpoint> endpoints) {
        this.projectId = projectId;
        this.endpoints = endpoints;
    }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public List<ApiEndpoint> getEndpoints() { return endpoints; }
    public void setEndpoints(List<ApiEndpoint> endpoints) { this.endpoints = endpoints; }
}
