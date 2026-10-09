package com.codetutor.backend.vector.dto;

public class VectorEmbeddingSummary {
    private Long projectId;
    private int totalChunks;
    private int vectorsGenerated;
    private String vectorDimension; // e.g., 1536 for OpenAI
    private String status;

    public VectorEmbeddingSummary() {}

    public VectorEmbeddingSummary(Long projectId, int totalChunks, int vectorsGenerated, String vectorDimension, String status) {
        this.projectId = projectId;
        this.totalChunks = totalChunks;
        this.vectorsGenerated = vectorsGenerated;
        this.vectorDimension = vectorDimension;
        this.status = status;
    }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }

    public int getVectorsGenerated() { return vectorsGenerated; }
    public void setVectorsGenerated(int vectorsGenerated) { this.vectorsGenerated = vectorsGenerated; }

    public String getVectorDimension() { return vectorDimension; }
    public void setVectorDimension(String vectorDimension) { this.vectorDimension = vectorDimension; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
