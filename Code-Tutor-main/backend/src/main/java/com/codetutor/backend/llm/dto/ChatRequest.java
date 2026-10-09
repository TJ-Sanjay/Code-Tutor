package com.codetutor.backend.llm.dto;

public class ChatRequest {
    private String query;
    private String selectedFile; // Optional context if user is looking at a specific file
    private String mode; // e.g. "QA", "EXPLAIN", "REFACTOR"

    public ChatRequest() {}

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getSelectedFile() { return selectedFile; }
    public void setSelectedFile(String selectedFile) { this.selectedFile = selectedFile; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}
