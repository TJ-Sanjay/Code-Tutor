package com.codetutor.backend.analyzer.service;

import com.codetutor.backend.analyzer.dto.SpringComponentsSummary;
import com.codetutor.backend.parser.dto.ClassStructure;
import com.codetutor.backend.parser.dto.CodeStructureSummary;
import com.codetutor.backend.parser.service.CodeStructureExtractorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Module 16 — Spring Component Detector.
 *
 * Identifies specific classes that act as Spring components:
 *   - Controllers (@RestController, @Controller)
 *   - Services (@Service)
 *   - Repositories (@Repository)
 *   - Entities (@Entity, @Document)
 *   - Configurations (@Configuration)
 *   - General Components (@Component)
 */
@Service
public class SpringComponentDetectorService {

    private static final Logger log = LoggerFactory.getLogger(SpringComponentDetectorService.class);

    private final CodeStructureExtractorService codeStructureExtractorService;

    public SpringComponentDetectorService(CodeStructureExtractorService codeStructureExtractorService) {
        this.codeStructureExtractorService = codeStructureExtractorService;
    }

    public SpringComponentsSummary detectComponents(Long projectId) {
        log.info("Starting Spring Component Detection for project id={}", projectId);

        CodeStructureSummary structureSummary = codeStructureExtractorService.extractCodeStructure(projectId);

        List<String> controllers = new ArrayList<>();
        List<String> services = new ArrayList<>();
        List<String> repositories = new ArrayList<>();
        List<String> entities = new ArrayList<>();
        List<String> configurations = new ArrayList<>();
        List<String> components = new ArrayList<>();

        if (structureSummary.getClasses() != null) {
            for (ClassStructure cls : structureSummary.getClasses()) {
                if (cls.getAnnotations() != null) {
                    for (String ann : cls.getAnnotations()) {
                        String lower = ann.toLowerCase();
                        if (lower.contains("controller")) {
                            controllers.add(cls.getName());
                            break; // Avoid double counting if a class has multiple stereotype-like annotations
                        } else if (lower.contains("service")) {
                            services.add(cls.getName());
                            break;
                        } else if (lower.contains("repository")) {
                            repositories.add(cls.getName());
                            break;
                        } else if (lower.contains("entity") || lower.contains("document")) {
                            entities.add(cls.getName());
                            break;
                        } else if (lower.contains("configuration")) {
                            configurations.add(cls.getName());
                            break;
                        } else if (lower.contains("component")) {
                            components.add(cls.getName());
                            break;
                        }
                    }
                }
            }
        }

        log.info("Detected components for project id={}: {} controllers, {} services, {} repositories", 
                projectId, controllers.size(), services.size(), repositories.size());

        return new SpringComponentsSummary(
                projectId,
                controllers,
                services,
                repositories,
                entities,
                configurations,
                components
        );
    }
}
