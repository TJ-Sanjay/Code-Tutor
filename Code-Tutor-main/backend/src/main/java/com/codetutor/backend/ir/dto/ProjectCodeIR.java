package com.codetutor.backend.ir.dto;

import com.codetutor.backend.annotation.dto.AnnotationSummary;
import com.codetutor.backend.analyzer.dto.ImportDependencySummary;
import com.codetutor.backend.detector.dto.TechStackResult;
import com.codetutor.backend.parser.dto.CodeStructureSummary;
import com.codetutor.backend.scanner.dto.ScanResult;

/**
 * Module 15: Intermediate Representation (IR) of the entire project.
 * Aggregates all analysis results into a single comprehensive JSON model.
 */
public class ProjectCodeIR {

    private Long projectId;
    private String generatedAt;
    
    private ScanResult fileSystem;
    private TechStackResult techStack;
    private CodeStructureSummary codeStructure;
    private AnnotationSummary annotations;
    private ImportDependencySummary dependencies;

    public ProjectCodeIR() {
    }

    public ProjectCodeIR(Long projectId, String generatedAt, ScanResult fileSystem, TechStackResult techStack, 
                         CodeStructureSummary codeStructure, AnnotationSummary annotations, ImportDependencySummary dependencies) {
        this.projectId = projectId;
        this.generatedAt = generatedAt;
        this.fileSystem = fileSystem;
        this.techStack = techStack;
        this.codeStructure = codeStructure;
        this.annotations = annotations;
        this.dependencies = dependencies;
    }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }

    public ScanResult getFileSystem() { return fileSystem; }
    public void setFileSystem(ScanResult fileSystem) { this.fileSystem = fileSystem; }

    public TechStackResult getTechStack() { return techStack; }
    public void setTechStack(TechStackResult techStack) { this.techStack = techStack; }

    public CodeStructureSummary getCodeStructure() { return codeStructure; }
    public void setCodeStructure(CodeStructureSummary codeStructure) { this.codeStructure = codeStructure; }

    public AnnotationSummary getAnnotations() { return annotations; }
    public void setAnnotations(AnnotationSummary annotations) { this.annotations = annotations; }

    public ImportDependencySummary getDependencies() { return dependencies; }
    public void setDependencies(ImportDependencySummary dependencies) { this.dependencies = dependencies; }
}
