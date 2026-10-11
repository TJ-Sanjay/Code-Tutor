package com.codetutor.backend.annotation.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Summary DTO of detected annotations across the codebase.
 */
public class AnnotationSummary {

    private Long projectId;
    private Map<String, Integer> stereotypeCounts;
    private Map<String, List<String>> classAnnotations;
    private Map<String, List<String>> methodAnnotations;

    public AnnotationSummary() {
        this.stereotypeCounts = new HashMap<>();
        this.classAnnotations = new HashMap<>();
        this.methodAnnotations = new HashMap<>();
    }

    public AnnotationSummary(
            Long projectId,
            Map<String, Integer> stereotypeCounts,
            Map<String, List<String>> classAnnotations,
            Map<String, List<String>> methodAnnotations
    ) {
        this.projectId = projectId;
        this.stereotypeCounts = stereotypeCounts;
        this.classAnnotations = classAnnotations;
        this.methodAnnotations = methodAnnotations;
    }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public Map<String, Integer> getStereotypeCounts() { return stereotypeCounts; }
    public void setStereotypeCounts(Map<String, Integer> stereotypeCounts) { this.stereotypeCounts = stereotypeCounts; }

    public Map<String, List<String>> getClassAnnotations() { return classAnnotations; }
    public void setClassAnnotations(Map<String, List<String>> classAnnotations) { this.classAnnotations = classAnnotations; }

    public Map<String, List<String>> getMethodAnnotations() { return methodAnnotations; }
    public void setMethodAnnotations(Map<String, List<String>> methodAnnotations) { this.methodAnnotations = methodAnnotations; }
}
