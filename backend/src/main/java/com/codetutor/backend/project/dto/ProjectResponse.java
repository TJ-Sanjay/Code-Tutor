package com.codetutor.backend.project.dto;

import com.codetutor.backend.project.model.Project;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/** Response DTO for a Project — returned from list / get / status endpoints. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectResponse {

    private Long id;
    private String name;
    private String description;
    private String sourceType;
    private String githubUrl;
    private String repositoryName;
    private String zipFileName;
    private Long zipFileSize;
    private String status;
    private String statusMessage;
    private Integer progressPercent;
    private boolean ready;
    private String primaryLanguage;
    private Integer fileCount;
    private Long sizeBytes;
    private Integer classCount;
    private Integer endpointCount;
    private Integer tableCount;
    private Integer moduleCount;
    private String techStackJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private ProjectResponse() {}

    public static ProjectResponse from(Project p) {
        ProjectResponse r = new ProjectResponse();
        r.id = p.getId();
        r.name = p.getName();
        r.description = p.getDescription();
        r.sourceType = p.getSourceType() != null ? p.getSourceType().name() : null;
        r.githubUrl = p.getGithubUrl();
        r.repositoryName = p.getRepositoryName();
        r.zipFileName = p.getZipFileName();
        r.zipFileSize = p.getZipFileSize();
        r.status = p.getStatus() != null ? p.getStatus().name() : null;
        r.statusMessage = p.getStatusMessage();
        r.progressPercent = p.getProgressPercent();
        r.ready = p.getStatus() == Project.AnalysisStatus.COMPLETED;
        r.primaryLanguage = p.getPrimaryLanguage();
        r.fileCount = p.getFileCount();
        r.sizeBytes = p.getSizeBytes();
        r.classCount = p.getClassCount();
        r.endpointCount = p.getEndpointCount();
        r.tableCount = p.getTableCount();
        r.moduleCount = p.getModuleCount();
        r.techStackJson = p.getTechStackJson();
        r.createdAt = p.getCreatedAt();
        r.updatedAt = p.getUpdatedAt();
        return r;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getSourceType() { return sourceType; }
    public String getGithubUrl() { return githubUrl; }
    public String getRepositoryName() { return repositoryName; }
    public String getZipFileName() { return zipFileName; }
    public Long getZipFileSize() { return zipFileSize; }
    public String getStatus() { return status; }
    public String getStatusMessage() { return statusMessage; }
    public Integer getProgressPercent() { return progressPercent; }
    public boolean isReady() { return ready; }
    public String getPrimaryLanguage() { return primaryLanguage; }
    public Integer getFileCount() { return fileCount; }
    public Long getSizeBytes() { return sizeBytes; }
    public Integer getClassCount() { return classCount; }
    public Integer getEndpointCount() { return endpointCount; }
    public Integer getTableCount() { return tableCount; }
    public Integer getModuleCount() { return moduleCount; }
    public String getTechStackJson() { return techStackJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
