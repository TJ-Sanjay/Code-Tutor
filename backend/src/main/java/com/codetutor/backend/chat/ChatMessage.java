package com.codetutor.backend.chat;

import com.codetutor.backend.auth.model.User;
import com.codetutor.backend.project.model.Project;
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

    /** Many messages belong to one project (foreign key chat_messages.project_id -> projects.id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** Many messages were written by one user (foreign key chat_messages.user_id -> users.id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

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

    public ChatMessage(Project project, User user, String role, String content, String sourcesJson, String flowJson) {
        this.project = project;
        this.user = user;
        this.role = role;
        this.content = content;
        this.sourcesJson = sourcesJson;
        this.flowJson = flowJson;
    }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public User getUser() { return user; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public String getSourcesJson() { return sourcesJson; }
    public String getFlowJson() { return flowJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
