package com.codetutor.backend.analyzer.service;

import com.codetutor.backend.analyzer.dto.ApiEndpoint;
import com.codetutor.backend.analyzer.dto.ApiEndpointSummary;
import com.codetutor.backend.parser.dto.ClassStructure;
import com.codetutor.backend.parser.dto.CodeStructureSummary;
import com.codetutor.backend.parser.dto.MethodStructure;
import com.codetutor.backend.parser.service.CodeStructureExtractorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Module 21 — API Analyzer.
 *
 * Detects REST API endpoints defined in Spring Controllers.
 * Extracts HTTP Methods (GET, POST, etc.) and URL paths.
 */
@Service
public class ApiAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(ApiAnalyzerService.class);

    private final CodeStructureExtractorService codeStructureExtractorService;

    public ApiAnalyzerService(CodeStructureExtractorService codeStructureExtractorService) {
        this.codeStructureExtractorService = codeStructureExtractorService;
    }

    public ApiEndpointSummary analyzeApis(Long projectId) {
        log.info("Starting API Analysis for project id={}", projectId);

        CodeStructureSummary structureSummary = codeStructureExtractorService.extractCodeStructure(projectId);
        List<ApiEndpoint> endpoints = new ArrayList<>();

        if (structureSummary.getClasses() != null) {
            for (ClassStructure cls : structureSummary.getClasses()) {
                if (isController(cls.getAnnotations())) {
                    String basePath = extractPath(cls.getAnnotations(), "@RequestMapping");

                    if (cls.getMethods() != null) {
                        for (MethodStructure method : cls.getMethods()) {
                            if (method.getAnnotations() != null) {
                                for (String ann : method.getAnnotations()) {
                                    String httpMethod = determineHttpMethod(ann);
                                    if (httpMethod != null) {
                                        String methodPath = extractPath(List.of(ann), null); // It might contain the path inside the mapping
                                        String fullPath = normalizePath(basePath + methodPath);
                                        endpoints.add(new ApiEndpoint(httpMethod, fullPath, cls.getName(), method.getName()));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        log.info("Detected {} API endpoints for project id={}", endpoints.size(), projectId);
        return new ApiEndpointSummary(projectId, endpoints);
    }

    private boolean isController(List<String> annotations) {
        if (annotations == null) return false;
        for (String ann : annotations) {
            if (ann.contains("RestController") || ann.contains("Controller")) {
                return true;
            }
        }
        return false;
    }

    private String determineHttpMethod(String annotation) {
        if (annotation.contains("GetMapping")) return "GET";
        if (annotation.contains("PostMapping")) return "POST";
        if (annotation.contains("PutMapping")) return "PUT";
        if (annotation.contains("DeleteMapping")) return "DELETE";
        if (annotation.contains("PatchMapping")) return "PATCH";
        if (annotation.contains("RequestMapping")) return "ANY"; // Generic
        return null;
    }

    private String extractPath(List<String> annotations, String targetAnnotation) {
        if (annotations == null) return "";
        for (String ann : annotations) {
            if (targetAnnotation == null || ann.contains(targetAnnotation)) {
                int startQuote = ann.indexOf('"');
                if (startQuote != -1) {
                    int endQuote = ann.indexOf('"', startQuote + 1);
                    if (endQuote != -1) {
                        return ann.substring(startQuote + 1, endQuote);
                    }
                }
            }
        }
        return "";
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) return "/";
        if (!path.startsWith("/")) path = "/" + path;
        path = path.replace("//", "/");
        return path;
    }
}
