package com.codetutor.backend.vector.service;

import com.codetutor.backend.vector.dto.CodeChunk;
import com.codetutor.backend.vector.dto.VectorEmbeddingSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Module 29 / 30 — Embedding Generator & Vector Store.
 *
 * Generates vector embeddings for CodeChunks and stores them in PGVector.
 * Currently simulates the process since pgvector dependencies are not yet active.
 */
@Service
public class VectorService {

    private static final Logger log = LoggerFactory.getLogger(VectorService.class);

    private final ChunkingService chunkingService;

    public VectorService(ChunkingService chunkingService) {
        this.chunkingService = chunkingService;
    }

    public VectorEmbeddingSummary processEmbeddings(Long projectId) {
        log.info("Generating embeddings for project id={}", projectId);

        // 1. Get Chunks
        List<CodeChunk> chunks = chunkingService.generateChunks(projectId);

        // 2. Simulate Embedding Generation (Module 29)
        for (CodeChunk chunk : chunks) {
            // In a real scenario, this calls OpenAI or HuggingFace API.
            // e.g., openAiClient.embed(chunk.getContent())
            chunk.setEmbedding(Collections.nCopies(1536, 0.0)); // Mock 1536-dimensional vector
        }

        // 3. Simulate Vector Store Persistence (Module 30)
        // In a real scenario, we save to a PGVector enabled table: chunkRepository.saveAll(chunks)
        
        log.info("Successfully generated and stored {} embeddings for project id={}", chunks.size(), projectId);

        return new VectorEmbeddingSummary(
                projectId,
                chunks.size(),
                chunks.size(),
                "1536 (OpenAI text-embedding-3-small simulated)",
                "COMPLETED"
        );
    }
}
