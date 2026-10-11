package com.codetutor.backend.project.model;

import com.codetutor.backend.auth.model.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Project entity — represents a codebase that a user has uploaded for analysis.
 * Each project is linked to a user and contains metadata about the source
 * (GitHub URL or ZIP), the analysis status, and workspace paths.
 */
@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    // ─── Source type ──────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    /** Raw GitHub URL entered by user (null for ZIP uploads) */
    @Column(name = "github_url", length = 512)
    private String githubUrl;

    /** Resolved owner/repo from GitHub URL (e.g. spring-projects/spring-boot) */
    @Column(name = "repository_name", length = 200)
    private String repositoryName;

    /** Original ZIP file name (null for GitHub imports) */
    @Column(name = "zip_file_name", length = 255)
    private String zipFileName;

    /** Size of uploaded ZIP in bytes */
    @Column(name = "zip_file_size")
    private Long zipFileSize;

    // ─── Workspace ────────────────────────────────────────────────────────
    /** Absolute path to the isolated workspace folder for this project */
    @Column(name = "workspace_path", length = 512)
    private String workspacePath;

    // ─── Analysis status ─────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus status = AnalysisStatus.PENDING;

    /** Human-readable progress message (e.g. "Parsing Java files...") */
    @Column(name = "status_message", length = 512)
    private String statusMessage;

    /** 0–100 progress percentage */
    @Column(name = "progress_percent")
    private Integer progressPercent = 0;

    // ─── Technology detection result ──────────────────────────────────────
    /** JSON string storing detected tech stack (e.g. {"Java":80,"XML":20}) */
    @Column(name = "tech_stack_json", columnDefinition = "TEXT")
    private String techStackJson;

    /** Primary language detected */
    @Column(name = "primary_language", length = 50)
    private String primaryLanguage;

    /** Total number of files scanned */
    @Column(name = "file_count")
    private Integer fileCount;

    // ─── Ownership ────────────────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ─── Enums ───────────────────────────────────────────────────────────

    public enum SourceType {
        GITHUB, ZIP
    }

    public enum AnalysisStatus {
        PENDING,        // Just created, not yet queued
        QUEUED,         // Queued for background processing
        CLONING,        // Cloning/extracting repository
        SCANNING,       // Scanning files/folders
        PARSING,        // Parsing code (Tree-sitter)
        ANALYZING,      // Building dependency graph, embeddings
        COMPLETED,      // Analysis done successfully
        FAILED          // Analysis failed with an error
    }

    // ─── Constructors ─────────────────────────────────────────────────────

    public Project() {}

    private Project(Builder b) {
        this.name = b.name;
        this.description = b.description;
        this.sourceType = b.sourceType;
        this.githubUrl = b.githubUrl;
        this.repositoryName = b.repositoryName;
        this.zipFileName = b.zipFileName;
        this.zipFileSize = b.zipFileSize;
        this.owner = b.owner;
        this.status = AnalysisStatus.PENDING;
        this.progressPercent = 0;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String name;
        private String description;
        private SourceType sourceType;
        private String githubUrl;
        private String repositoryName;
        private String zipFileName;
        private Long zipFileSize;
        private User owner;

        public Builder name(String v) { this.name = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder sourceType(SourceType v) { this.sourceType = v; return this; }
        public Builder githubUrl(String v) { this.githubUrl = v; return this; }
        public Builder repositoryName(String v) { this.repositoryName = v; return this; }
        public Builder zipFileName(String v) { this.zipFileName = v; return this; }
        public Builder zipFileSize(Long v) { this.zipFileSize = v; return this; }
        public Builder owner(User v) { this.owner = v; return this; }

        public Project build() { return new Project(this); }
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public SourceType getSourceType() { return sourceType; }
    public void setSourceType(SourceType sourceType) { this.sourceType = sourceType; }

    public String getGithubUrl() { return githubUrl; }
    public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }

    public String getRepositoryName() { return repositoryName; }
    public void setRepositoryName(String repositoryName) { this.repositoryName = repositoryName; }

    public String getZipFileName() { return zipFileName; }
    public void setZipFileName(String zipFileName) { this.zipFileName = zipFileName; }

    public Long getZipFileSize() { return zipFileSize; }
    public void setZipFileSize(Long zipFileSize) { this.zipFileSize = zipFileSize; }

    public String getWorkspacePath() { return workspacePath; }
    public void setWorkspacePath(String workspacePath) { this.workspacePath = workspacePath; }

    public AnalysisStatus getStatus() { return status; }
    public void setStatus(AnalysisStatus status) { this.status = status; }

    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }

    public Integer getProgressPercent() { return progressPercent; }
    public void setProgressPercent(Integer progressPercent) { this.progressPercent = progressPercent; }

    public String getTechStackJson() { return techStackJson; }
    public void setTechStackJson(String techStackJson) { this.techStackJson = techStackJson; }

    public String getPrimaryLanguage() { return primaryLanguage; }
    public void setPrimaryLanguage(String primaryLanguage) { this.primaryLanguage = primaryLanguage; }

    public Integer getFileCount() { return fileCount; }
    public void setFileCount(Integer fileCount) { this.fileCount = fileCount; }

    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
