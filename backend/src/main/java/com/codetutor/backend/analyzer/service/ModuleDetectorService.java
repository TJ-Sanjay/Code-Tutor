package com.codetutor.backend.analyzer.service;

import com.codetutor.backend.analyzer.dto.LogicalModule;
import com.codetutor.backend.analyzer.dto.ModuleDetectionSummary;
import com.codetutor.backend.parser.dto.ClassStructure;
import com.codetutor.backend.parser.dto.CodeStructureSummary;
import com.codetutor.backend.parser.service.CodeStructureExtractorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Module 23 — Module Detector.
 *
 * Groups related code into logical modules (e.g., Auth, User, Project) 
 * by analyzing package structures and file paths.
 */
@Service
public class ModuleDetectorService {

    private static final Logger log = LoggerFactory.getLogger(ModuleDetectorService.class);

    private final CodeStructureExtractorService codeStructureExtractorService;

    public ModuleDetectorService(CodeStructureExtractorService codeStructureExtractorService) {
        this.codeStructureExtractorService = codeStructureExtractorService;
    }

    public ModuleDetectionSummary detectModules(Long projectId) {
        log.info("Starting Module Detection for project id={}", projectId);

        CodeStructureSummary structureSummary = codeStructureExtractorService.extractCodeStructure(projectId);
        Map<String, List<String>> modulesMap = new HashMap<>();

        if (structureSummary.getClasses() != null) {
            for (ClassStructure cls : structureSummary.getClasses()) {
                String moduleName = extractModuleName(cls.getFilePath());
                modulesMap.computeIfAbsent(moduleName, k -> new ArrayList<>()).add(cls.getName());
            }
        }

        List<LogicalModule> modules = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : modulesMap.entrySet()) {
            modules.add(new LogicalModule(entry.getKey(), entry.getValue()));
        }

        log.info("Detected {} logical modules for project id={}", modules.size(), projectId);
        return new ModuleDetectionSummary(projectId, modules);
    }

    private String extractModuleName(String filePath) {
        if (filePath == null) return "Common";
        
        // Normalize path
        String path = filePath.replace("\\", "/");
        
        // Remove file name
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash != -1) {
            path = path.substring(0, lastSlash);
        }

        // Try to identify standard package structures like src/main/java/com/company/project/module/controller
        String[] parts = path.split("/");
        
        // Very basic heuristic: Look for common structural directories (controller, service, repository, model, dto)
        // The directory BEFORE that is usually the module name.
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].toLowerCase();
            if (part.equals("controller") || part.equals("service") || 
                part.equals("repository") || part.equals("model") || 
                part.equals("dto") || part.equals("entity")) {
                if (i > 0) {
                    return capitalize(parts[i - 1]);
                }
            }
        }

        // Fallback to the last directory name if it's not a common root
        if (parts.length > 0) {
            String lastDir = parts[parts.length - 1];
            if (!lastDir.equals("java") && !lastDir.equals("main") && !lastDir.equals("src")) {
                return capitalize(lastDir);
            }
        }

        return "Core";
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
