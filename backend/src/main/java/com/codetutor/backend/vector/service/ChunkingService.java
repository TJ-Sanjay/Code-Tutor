package com.codetutor.backend.vector.service;

import com.codetutor.backend.parser.dto.ClassStructure;
import com.codetutor.backend.parser.dto.CodeStructureSummary;
import com.codetutor.backend.parser.dto.MethodStructure;
import com.codetutor.backend.parser.service.CodeStructureExtractorService;
import com.codetutor.backend.scanner.service.FileScannerService;
import com.codetutor.backend.vector.dto.CodeChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Module 28 — Chunking Engine.
 *
 * Breaks large Java files and JSON Code IRs into text chunks optimized for LLM context windows.
 * Currently chunks at the Method and Class semantic level using AST data.
 */
@Service
public class ChunkingService {

    private static final Logger log = LoggerFactory.getLogger(ChunkingService.class);

    private final CodeStructureExtractorService codeStructureExtractorService;
    private final FileScannerService fileScannerService;

    public ChunkingService(CodeStructureExtractorService codeStructureExtractorService, FileScannerService fileScannerService) {
        this.codeStructureExtractorService = codeStructureExtractorService;
        this.fileScannerService = fileScannerService;
    }

    public List<CodeChunk> generateChunks(Long projectId) {
        log.info("Starting Chunking Engine for project id={}", projectId);

        CodeStructureSummary structureSummary = codeStructureExtractorService.extractCodeStructure(projectId);
        List<CodeChunk> chunks = new ArrayList<>();

        if (structureSummary.getClasses() != null) {
            for (ClassStructure cls : structureSummary.getClasses()) {
                
                // Fetch file content
                String fileContent;
                try {
                    fileContent = fileScannerService.getFileContent(projectId, cls.getFilePath());
                } catch (Exception e) {
                    log.warn("Could not read file {} for chunking: {}", cls.getFilePath(), e.getMessage());
                    continue;
                }
                
                String[] lines = fileContent.split("\\r?\\n");

                // 1. Create a chunk for the Class (excluding method bodies if too large, or just take first N lines)
                // For simplicity, we just extract the class header and fields as a chunk
                String classHeader = extractLines(lines, cls.getStartLine(), cls.getMethods().isEmpty() ? cls.getEndLine() : cls.getMethods().get(0).getStartLine() - 1);
                chunks.add(new CodeChunk(
                        UUID.randomUUID().toString(),
                        projectId,
                        cls.getFilePath(),
                        "CLASS_SIGNATURE",
                        "Class: " + cls.getName() + "\\n" + classHeader
                ));

                // 2. Create chunks for each Method
                if (cls.getMethods() != null) {
                    for (MethodStructure method : cls.getMethods()) {
                        String methodContent = extractLines(lines, method.getStartLine(), method.getEndLine());
                        chunks.add(new CodeChunk(
                                UUID.randomUUID().toString(),
                                projectId,
                                cls.getFilePath(),
                                "METHOD",
                                "Method: " + cls.getName() + "." + method.getName() + "()\\n" + methodContent
                        ));
                    }
                }
            }
        }

        log.info("Chunking completed for project id={}. Generated {} chunks.", projectId, chunks.size());
        return chunks;
    }

    private String extractLines(String[] lines, int startLine, int endLine) {
        StringBuilder sb = new StringBuilder();
        // startLine and endLine are 1-indexed
        int start = Math.max(0, startLine - 1);
        int end = Math.min(lines.length - 1, endLine - 1);
        
        if (start > end) return "";

        for (int i = start; i <= end; i++) {
            sb.append(lines[i]).append("\\n");
        }
        return sb.toString();
    }
}
