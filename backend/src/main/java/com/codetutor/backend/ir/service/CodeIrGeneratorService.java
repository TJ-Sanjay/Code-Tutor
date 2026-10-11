package com.codetutor.backend.ir.service;

import com.codetutor.backend.annotation.service.AnnotationExtractorService;
import com.codetutor.backend.analyzer.service.ImportAnalyzerService;
import com.codetutor.backend.detector.service.TechnologyDetectorService;
import com.codetutor.backend.ir.dto.ProjectCodeIR;
import com.codetutor.backend.parser.service.CodeStructureExtractorService;
import com.codetutor.backend.scanner.service.FileScannerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Module 15 — Code IR Generator Service.
 *
 * Aggregates all extracted project artifacts (ASTs, dependencies, structure, annotations)
 * into a single unified JSON tree representing the complete understanding of the repository.
 */
@Service
public class CodeIrGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(CodeIrGeneratorService.class);

    private final FileScannerService fileScannerService;
    private final TechnologyDetectorService technologyDetectorService;
    private final CodeStructureExtractorService codeStructureExtractorService;
    private final AnnotationExtractorService annotationExtractorService;
    private final ImportAnalyzerService importAnalyzerService;

    public CodeIrGeneratorService(
            FileScannerService fileScannerService,
            TechnologyDetectorService technologyDetectorService,
            CodeStructureExtractorService codeStructureExtractorService,
            AnnotationExtractorService annotationExtractorService,
            ImportAnalyzerService importAnalyzerService
    ) {
        this.fileScannerService = fileScannerService;
        this.technologyDetectorService = technologyDetectorService;
        this.codeStructureExtractorService = codeStructureExtractorService;
        this.annotationExtractorService = annotationExtractorService;
        this.importAnalyzerService = importAnalyzerService;
    }

    public ProjectCodeIR generateProjectIR(Long projectId) {
        log.info("Generating complete Code IR for project id={}", projectId);

        return new ProjectCodeIR(
                projectId,
                LocalDateTime.now().toString(),
                fileScannerService.scanWorkspace(projectId),
                technologyDetectorService.detectTechStack(projectId),
                codeStructureExtractorService.extractCodeStructure(projectId),
                annotationExtractorService.extractAnnotations(projectId),
                importAnalyzerService.analyzeImports(projectId)
        );
    }
}
