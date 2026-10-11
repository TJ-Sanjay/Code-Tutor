package com.codetutor.backend.annotation.service;

import com.codetutor.backend.annotation.dto.AnnotationSummary;
import com.codetutor.backend.parser.dto.ClassStructure;
import com.codetutor.backend.parser.dto.CodeStructureSummary;
import com.codetutor.backend.parser.dto.MethodStructure;
import com.codetutor.backend.parser.service.CodeStructureExtractorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Module 13 — Annotation Extractor Service.
 *
 * Detects and classifies Spring & JPA annotations:
 *   1. Stereotypes: @RestController, @Controller, @Service, @Repository, @Entity, @Configuration, @Component
 *   2. Request Mappings: @GetMapping, @PostMapping, @PutMapping, @DeleteMapping, @RequestMapping
 *   3. Dependency Injection: @Autowired, @Qualifier, @Bean
 *   4. Groups annotations by Class and Method levels for architectural analysis
 */
@Service
public class AnnotationExtractorService {

    private static final Logger log = LoggerFactory.getLogger(AnnotationExtractorService.class);

    private final CodeStructureExtractorService codeStructureExtractorService;

    public AnnotationExtractorService(CodeStructureExtractorService codeStructureExtractorService) {
        this.codeStructureExtractorService = codeStructureExtractorService;
    }

    /**
     * Extracts and summarizes all annotations across a project workspace.
     */
    public AnnotationSummary extractAnnotations(Long projectId) {
        CodeStructureSummary structureSummary = codeStructureExtractorService.extractCodeStructure(projectId);

        Map<String, Integer> stereotypeCounts = new HashMap<>();
        stereotypeCounts.put("CONTROLLER", 0);
        stereotypeCounts.put("SERVICE", 0);
        stereotypeCounts.put("REPOSITORY", 0);
        stereotypeCounts.put("ENTITY", 0);
        stereotypeCounts.put("CONFIGURATION", 0);
        stereotypeCounts.put("COMPONENT", 0);

        Map<String, List<String>> classAnnotations = new HashMap<>();
        Map<String, List<String>> methodAnnotations = new HashMap<>();

        for (ClassStructure cls : structureSummary.getClasses()) {
            if (cls.getAnnotations() != null && !cls.getAnnotations().isEmpty()) {
                classAnnotations.put(cls.getName(), cls.getAnnotations());

                for (String ann : cls.getAnnotations()) {
                    String lower = ann.toLowerCase();
                    if (lower.contains("controller")) {
                        stereotypeCounts.put("CONTROLLER", stereotypeCounts.get("CONTROLLER") + 1);
                    } else if (lower.contains("service")) {
                        stereotypeCounts.put("SERVICE", stereotypeCounts.get("SERVICE") + 1);
                    } else if (lower.contains("repository")) {
                        stereotypeCounts.put("REPOSITORY", stereotypeCounts.get("REPOSITORY") + 1);
                    } else if (lower.contains("entity")) {
                        stereotypeCounts.put("ENTITY", stereotypeCounts.get("ENTITY") + 1);
                    } else if (lower.contains("configuration")) {
                        stereotypeCounts.put("CONFIGURATION", stereotypeCounts.get("CONFIGURATION") + 1);
                    } else if (lower.contains("component")) {
                        stereotypeCounts.put("COMPONENT", stereotypeCounts.get("COMPONENT") + 1);
                    }
                }
            }

            if (cls.getMethods() != null) {
                for (MethodStructure method : cls.getMethods()) {
                    if (method.getAnnotations() != null && !method.getAnnotations().isEmpty()) {
                        String key = cls.getName() + "." + method.getName() + "()";
                        methodAnnotations.put(key, method.getAnnotations());
                    }
                }
            }
        }

        log.info("Extracted annotations for project id={}: stereotypes={}", projectId, stereotypeCounts);

        return new AnnotationSummary(
                projectId,
                stereotypeCounts,
                classAnnotations,
                methodAnnotations
        );
    }
}
