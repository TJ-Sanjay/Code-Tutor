package com.codetutor.backend.llm.service;

import com.codetutor.backend.vector.dto.CodeChunk;
import com.codetutor.backend.vector.service.ChunkingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Module 31 — Vector Search Engine.
 *
 * Given a user query, generates its embedding and queries the database
 * using Cosine Similarity to find relevant code chunks.
 */
@Service
public class VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchService.class);

    private final ChunkingService chunkingService;

    public VectorSearchService(ChunkingService chunkingService) {
        this.chunkingService = chunkingService;
    }

    public List<CodeChunk> searchSimilarChunks(Long projectId, String query, int topK) {
        log.info("Searching for top {} chunks for project id={} with query: '{}'", topK, projectId, query);

        // Simulation: Since PGVector isn't wired up, we generate chunks on the fly
        // and return the first few as a simulated "search result".
        List<CodeChunk> allChunks = chunkingService.generateChunks(projectId);
        
        // Mock semantic search: just filter chunks that contain keywords from the query
        // If none found, return the top K chunks randomly.
        String lowerQuery = query.toLowerCase();
        List<CodeChunk> matched = allChunks.stream()
                .filter(c -> c.getContent().toLowerCase().contains(lowerQuery) || c.getFilePath().toLowerCase().contains(lowerQuery))
                .limit(topK)
                .collect(Collectors.toList());

        if (matched.isEmpty()) {
            return allChunks.stream().limit(topK).collect(Collectors.toList());
        }

        return matched;
    }
}
