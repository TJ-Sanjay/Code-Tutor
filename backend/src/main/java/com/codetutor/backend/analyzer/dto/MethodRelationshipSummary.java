package com.codetutor.backend.analyzer.dto;

import java.util.List;
import java.util.Map;

public class MethodRelationshipSummary {

    private Long projectId;
    
    // Maps a ClassName.methodName() to a list of called method names
    // e.g. "AuthController.register" -> ["authService.register", "ResponseEntity.status"]
    private Map<String, List<String>> methodCalls;

    public MethodRelationshipSummary() {
    }

    public MethodRelationshipSummary(Long projectId, Map<String, List<String>> methodCalls) {
        this.projectId = projectId;
        this.methodCalls = methodCalls;
    }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public Map<String, List<String>> getMethodCalls() { return methodCalls; }
    public void setMethodCalls(Map<String, List<String>> methodCalls) { this.methodCalls = methodCalls; }
}
