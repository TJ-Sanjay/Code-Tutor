package com.codetutor.backend.llm.dto;

import java.util.List;

public class ChatResponse {
    private String answer;
    private List<String> retrievedSources; // List of file paths or chunk IDs that were used as context

    public ChatResponse() {}

    public ChatResponse(String answer, List<String> retrievedSources) {
        this.answer = answer;
        this.retrievedSources = retrievedSources;
    }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public List<String> getRetrievedSources() { return retrievedSources; }
    public void setRetrievedSources(List<String> retrievedSources) { this.retrievedSources = retrievedSources; }
}
