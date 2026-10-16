package com.codetutor.backend.vector.dto;

import java.util.List;

public class CodeChunk {
    private String id;
    private Long projectId;
    private String filePath;
    private String nodeType; // CLASS, METHOD, or IR
    private String content;
    private List<Double> embedding; // Mocked for now

    public CodeChunk() {}

    public CodeChunk(String id, Long projectId, String filePath, String nodeType, String content) {
        this.id = id;
        this.projectId = projectId;
        this.filePath = filePath;
        this.nodeType = nodeType;
        this.content = content;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<Double> getEmbedding() { return embedding; }
    public void setEmbedding(List<Double> embedding) { this.embedding = embedding; }
}
