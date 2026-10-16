package com.codetutor.backend.llm.service;

import com.codetutor.backend.ir.dto.ProjectCodeIR;
import com.codetutor.backend.ir.service.CodeIrGeneratorService;
import com.codetutor.backend.llm.dto.ChatRequest;
import com.codetutor.backend.llm.dto.ChatResponse;
import com.codetutor.backend.vector.dto.CodeChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Modules 32-36 — Chat Engine (LangChain/LLM Orchestrator).
 *
 * Handles context assembly, RAG retrieval, and simulated LLM generation.
 */
@Service
public class ChatEngineService {

    private static final Logger log = LoggerFactory.getLogger(ChatEngineService.class);

    private final VectorSearchService vectorSearchService;
    private final CodeIrGeneratorService codeIrGeneratorService;

    public ChatEngineService(VectorSearchService vectorSearchService, CodeIrGeneratorService codeIrGeneratorService) {
        this.vectorSearchService = vectorSearchService;
        this.codeIrGeneratorService = codeIrGeneratorService;
    }

    public ChatResponse handleChat(Long projectId, ChatRequest request) {
        log.info("Processing chat request for project id={}, mode={}", projectId, request.getMode());

        // Module 31 & 32: Context Retrieval
        List<CodeChunk> contextChunks = vectorSearchService.searchSimilarChunks(projectId, request.getQuery(), 3);
        List<String> retrievedSources = contextChunks.stream().map(CodeChunk::getFilePath).distinct().collect(Collectors.toList());

        // Add specific file context if provided
        if (request.getSelectedFile() != null && !request.getSelectedFile().isEmpty()) {
            retrievedSources.add(request.getSelectedFile());
        }

        // Fetch IR for high-level architecture overview (Module 32)
        ProjectCodeIR ir = codeIrGeneratorService.generateProjectIR(projectId);

        // Build Prompt Context (Module 34)
        String promptContext = buildPromptContext(request, contextChunks, ir);

        // Module 35 & 36: Simulate LLM Generation (Since we don't have an OpenAI key hooked up)
        String simulatedResponse = simulateLlmResponse(request.getMode(), request.getQuery(), retrievedSources);

        return new ChatResponse(simulatedResponse, retrievedSources);
    }

    private String buildPromptContext(ChatRequest request, List<CodeChunk> chunks, ProjectCodeIR ir) {
        StringBuilder sb = new StringBuilder();
        sb.append("System Instructions: You are CodeTutor, an AI assistant analyzing a Java Spring Boot project.\\n");
        sb.append("Mode: ").append(request.getMode()).append("\\n\\n");
        
        sb.append("Project Tech Stack: ").append(ir.getTechStack().getFrameworks()).append("\\n");
        sb.append("Total Classes: ").append(ir.getCodeStructure().getTotalClasses()).append("\\n\\n");

        sb.append("Relevant Code Context:\\n");
        for (CodeChunk chunk : chunks) {
            sb.append("--- File: ").append(chunk.getFilePath()).append(" ---\\n");
            sb.append(chunk.getContent()).append("\\n\\n");
        }
        
        sb.append("User Query: ").append(request.getQuery()).append("\\n");
        return sb.toString();
    }

    private String simulateLlmResponse(String mode, String query, List<String> sources) {
        if ("EXPLAIN".equalsIgnoreCase(mode)) {
            return "Based on the retrieved code chunks, this code primarily handles " + 
                   "the domain logic and REST API exposure. It uses standard Spring stereotypes like " +
                   "@RestController and @Service. The file " + (sources.isEmpty() ? "in question" : sources.get(0)) + 
                   " is responsible for receiving HTTP requests and delegating to the service layer.";
        } else if ("REFACTOR".equalsIgnoreCase(mode)) {
            return "A suggested refactoring for this code would be to extract the business logic into a separate " +
                   "Service class if it's currently in the Controller, and ensure you are using Dependency Injection " +
                   "properly. Consider introducing a DTO mapper pattern to avoid exposing Entities directly.";
        } else {
            return "This is a simulated AI response to your question: '" + query + "'. " +
                   "The backend RAG pipeline successfully retrieved " + sources.size() + " relevant files to build the context. " +
                   "To get real LLM answers, an OpenAI or local LangChain4j integration can be enabled here.";
        }
    }
}
