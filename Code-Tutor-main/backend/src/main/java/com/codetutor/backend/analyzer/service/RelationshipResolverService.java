package com.codetutor.backend.analyzer.service;

import com.codetutor.backend.analyzer.dto.ClassRelationship;
import com.codetutor.backend.analyzer.dto.ImportDependencySummary;
import com.codetutor.backend.parser.dto.ClassStructure;
import com.codetutor.backend.parser.dto.CodeStructureSummary;
import com.codetutor.backend.parser.service.CodeStructureExtractorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Module 18 — Relationship Resolver.
 *
 * Combines code structure and imports to build a clear class-to-class dependency graph.
 * Resolves fields to injected component types.
 */
@Service
public class RelationshipResolverService {

    private static final Logger log = LoggerFactory.getLogger(RelationshipResolverService.class);

    private final CodeStructureExtractorService codeStructureExtractorService;
    private final ImportAnalyzerService importAnalyzerService;

    public RelationshipResolverService(CodeStructureExtractorService codeStructureExtractorService, ImportAnalyzerService importAnalyzerService) {
        this.codeStructureExtractorService = codeStructureExtractorService;
        this.importAnalyzerService = importAnalyzerService;
    }

    public List<ClassRelationship> resolveRelationships(Long projectId) {
        log.info("Resolving relationships for project id={}", projectId);

        CodeStructureSummary structureSummary = codeStructureExtractorService.extractCodeStructure(projectId);
        ImportDependencySummary importSummary = importAnalyzerService.analyzeImports(projectId);

        List<ClassRelationship> relationships = new ArrayList<>();
        Set<String> internalClassNames = new HashSet<>();
        
        if (structureSummary.getClasses() == null) return relationships;

        for (ClassStructure cls : structureSummary.getClasses()) {
            internalClassNames.add(cls.getName());
        }

        for (ClassStructure cls : structureSummary.getClasses()) {
            String sourceClass = cls.getName();

            // 1. Resolve field injections
            if (cls.getFields() != null) {
                for (String fieldDesc : cls.getFields()) {
                    // Field desc is roughly "Type name", e.g. "AuthService authService"
                    String[] parts = fieldDesc.split(" ");
                    if (parts.length >= 2) {
                        String fieldType = parts[0];
                        // Remove generics e.g. List<User> -> List
                        if (fieldType.contains("<")) {
                            fieldType = fieldType.substring(0, fieldType.indexOf('<'));
                        }
                        if (internalClassNames.contains(fieldType)) {
                            relationships.add(new ClassRelationship(sourceClass, fieldType, "INJECTS"));
                        }
                    }
                }
            }

            // 2. Resolve imports (using ImportAnalyzer)
            List<String> fileImports = importSummary.getInternalDependencies().get(cls.getName());
            if (fileImports != null) {
                for (String imp : fileImports) {
                    String targetClass = imp;
                    int lastDot = imp.lastIndexOf('.');
                    if (lastDot != -1) {
                        targetClass = imp.substring(lastDot + 1);
                    }
                    if (!targetClass.equals(sourceClass) && internalClassNames.contains(targetClass)) {
                        // Avoid duplicating an INJECTS relationship with an IMPORTS relationship
                        boolean alreadyInjects = relationships.stream()
                            .anyMatch(r -> r.getSourceClass().equals(sourceClass) && 
                                           r.getTargetClass().equals(targetClass) && 
                                           r.getRelationshipType().equals("INJECTS"));
                        if (!alreadyInjects) {
                            relationships.add(new ClassRelationship(sourceClass, targetClass, "IMPORTS"));
                        }
                    }
                }
            }
        }

        log.info("Resolved {} relationships for project id={}", relationships.size(), projectId);
        return relationships;
    }
}
