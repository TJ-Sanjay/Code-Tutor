package com.codetutor.backend.analyzer.service;

import com.codetutor.backend.analyzer.dto.MethodRelationshipSummary;
import com.codetutor.backend.parser.dto.ClassStructure;
import com.codetutor.backend.parser.dto.CodeStructureSummary;
import com.codetutor.backend.parser.dto.MethodStructure;
import com.codetutor.backend.parser.service.CodeStructureExtractorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Module 17 — Method Call Analyzer Service.
 *
 * Extracts method call traces inside a project workspace.
 * Identifies which methods call other methods (e.g. Controller method calls Service method).
 */
@Service
public class MethodCallAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(MethodCallAnalyzerService.class);

    private final CodeStructureExtractorService codeStructureExtractorService;

    public MethodCallAnalyzerService(CodeStructureExtractorService codeStructureExtractorService) {
        this.codeStructureExtractorService = codeStructureExtractorService;
    }

    public MethodRelationshipSummary analyzeMethodCalls(Long projectId) {
        log.info("Starting Method Call Analysis for project id={}", projectId);

        CodeStructureSummary structureSummary = codeStructureExtractorService.extractCodeStructure(projectId);
        Map<String, List<String>> methodCalls = new HashMap<>();

        if (structureSummary.getClasses() != null) {
            for (ClassStructure cls : structureSummary.getClasses()) {
                if (cls.getMethods() != null) {
                    for (MethodStructure method : cls.getMethods()) {
                        String methodKey = cls.getName() + "." + method.getName() + "()";
                        // Using the calledMethods extracted by JavaParserService
                        // These are currently just raw method names (e.g., "save", "findAll")
                        // Module 18 (Relationship Resolver) will map these to specific class instances
                        methodCalls.put(methodKey, method.getCalledMethods()); 
                        // Wait, JavaParserService puts called methods in JavaAstNode, but does CodeStructureExtractorService copy it?
                    }
                }
            }
        }

        log.info("Method call analysis complete for project id={}", projectId);
        return new MethodRelationshipSummary(projectId, methodCalls);
    }
}
