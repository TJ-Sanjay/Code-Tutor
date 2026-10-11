package com.codetutor.backend.chat;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** One persisted chat message. Every row is tied to both a project and the user who owns it. */
@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_project_user", columnList = "project_id,user_id")
})
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** USER or ASSISTANT */
    @Column(nullable = false, length = 12)
    private String role;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /** JSON array of source references (assistant messages) */
    @Column(name = "sources_json", columnDefinition = "TEXT")
    private String sourcesJson;

    /** JSON of the generated flow diagram (assistant messages) */
    @Column(name = "flow_json", columnDefinition = "TEXT")
    private String flowJson;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public ChatMessage() {}

    public ChatMessage(Long projectId, Long userId, String role, String content, String sourcesJson, String flowJson) {
        this.projectId = projectId;
        this.userId = userId;
        this.role = role;
        this.content = content;
        this.sourcesJson = sourcesJson;
        this.flowJson = flowJson;
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public Long getUserId() { return userId; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public String getSourcesJson() { return sourcesJson; }
    public String getFlowJson() { return flowJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
