package com.codetutor.backend.analyzer.service;

import com.codetutor.backend.analyzer.dto.ImportDependencySummary;
import com.codetutor.backend.parser.dto.ClassStructure;
import com.codetutor.backend.parser.dto.CodeStructureSummary;
import com.codetutor.backend.parser.dto.JavaAstNode;
import com.codetutor.backend.parser.service.CodeStructureExtractorService;
import com.codetutor.backend.parser.service.JavaParserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Module 14 — Import Analyzer Service.
 *
 * Extracts imports and determines internal vs external dependencies:
 *   1. Identifies all imported types per file.
 *   2. Classifies dependencies as internal (project classes) vs external (libraries/JDK).
 *   3. Maps dependencies to build an import dependency graph.
 */
@Service
public class ImportAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(ImportAnalyzerService.class);

    private final JavaParserService javaParserService;
    private final CodeStructureExtractorService codeStructureExtractorService;

    public ImportAnalyzerService(JavaParserService javaParserService, CodeStructureExtractorService codeStructureExtractorService) {
        this.javaParserService = javaParserService;
        this.codeStructureExtractorService = codeStructureExtractorService;
    }

    public ImportDependencySummary analyzeImports(Long projectId) {
        log.info("Starting Import Analysis for project id={}", projectId);

        // Get AST nodes which contain file-level imports
        List<JavaAstNode> fileAsts = javaParserService.parseWorkspaceJavaFiles(projectId);
        
        // Get extracted structure to know internal classes
        CodeStructureSummary structureSummary = codeStructureExtractorService.extractCodeStructure(projectId);
        
        Set<String> internalClassNames = new HashSet<>();
        if (structureSummary.getClasses() != null) {
            for (ClassStructure cls : structureSummary.getClasses()) {
                internalClassNames.add(cls.getName());
            }
        }

        Map<String, List<String>> internalDependencies = new HashMap<>();
        Map<String, List<String>> externalDependencies = new HashMap<>();

        int totalInternal = 0;
        int totalExternal = 0;

        for (JavaAstNode fileAst : fileAsts) {
            String fileName = fileAst.getName();
            
            List<String> fileInternal = new ArrayList<>();
            List<String> fileExternal = new ArrayList<>();

            if (fileAst.getImportedTypes() != null) {
                for (String importedType : fileAst.getImportedTypes()) {
                    String simpleName = importedType;
                    int lastDot = importedType.lastIndexOf('.');
                    if (lastDot != -1 && lastDot < importedType.length() - 1) {
                        simpleName = importedType.substring(lastDot + 1);
                    }

                    // Heuristic: If the imported class name matches any of our project classes, it is internal.
                    if (internalClassNames.contains(simpleName)) {
                        fileInternal.add(importedType);
                        totalInternal++;
                    } else {
                        fileExternal.add(importedType);
                        totalExternal++;
                    }
                }
            }

            internalDependencies.put(fileName, fileInternal);
            externalDependencies.put(fileName, fileExternal);
        }

        log.info("Import analysis complete for project id={}. internal={}, external={}", projectId, totalInternal, totalExternal);

        return new ImportDependencySummary(
                projectId,
                fileAsts.size(),
                totalInternal,
                totalExternal,
                internalDependencies,
                externalDependencies
        );
    }
}
