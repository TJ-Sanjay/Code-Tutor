package com.codetutor.backend.project.controller;

import com.codetutor.backend.common.response.ApiResponse;
import com.codetutor.backend.project.dto.CreateGithubProjectRequest;
import com.codetutor.backend.project.dto.ProjectResponse;
import com.codetutor.backend.project.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import com.codetutor.backend.annotation.dto.AnnotationSummary;
import com.codetutor.backend.annotation.service.AnnotationExtractorService;
import com.codetutor.backend.detector.dto.TechStackResult;
import com.codetutor.backend.detector.service.TechnologyDetectorService;
import com.codetutor.backend.parser.dto.CodeStructureSummary;
import com.codetutor.backend.parser.dto.JavaAstNode;
import com.codetutor.backend.parser.service.CodeStructureExtractorService;
import com.codetutor.backend.parser.service.JavaParserService;
import com.codetutor.backend.scanner.dto.ScanResult;
import com.codetutor.backend.scanner.service.FileScannerService;

/**
 * Project Management REST controller.
 * Context Path: /api/projects
 *
 * Module 3: Project CRUD & List
 * Module 4: GitHub Project Creation
 * Module 5: ZIP File Upload Project Creation
 * Module 8: Workspace File Scanner Tree
 * Module 10: Technology Stack Detector
 * Module 11: Java AST Parser
 * Module 12: Code Structure Extractor
 * Module 13: Annotation Extractor
 */
@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final FileScannerService fileScannerService;
    private final TechnologyDetectorService technologyDetectorService;
    private final JavaParserService javaParserService;
    private final CodeStructureExtractorService codeStructureExtractorService;
    private final AnnotationExtractorService annotationExtractorService;
    private final com.codetutor.backend.analyzer.service.ImportAnalyzerService importAnalyzerService;
    private final com.codetutor.backend.ir.service.CodeIrGeneratorService codeIrGeneratorService;
    private final com.codetutor.backend.graph.service.DependencyGraphBuilderService dependencyGraphBuilderService;
    private final com.codetutor.backend.analyzer.service.ApiAnalyzerService apiAnalyzerService;
    private final com.codetutor.backend.analyzer.service.DatabaseAnalyzerService databaseAnalyzerService;
    private final com.codetutor.backend.analyzer.service.ModuleDetectorService moduleDetectorService;
    private final com.codetutor.backend.vector.service.VectorService vectorService;
    private final com.codetutor.backend.llm.service.ChatEngineService chatEngineService;

    public ProjectController(
            ProjectService projectService,
            FileScannerService fileScannerService,
            TechnologyDetectorService technologyDetectorService,
            JavaParserService javaParserService,
            CodeStructureExtractorService codeStructureExtractorService,
            AnnotationExtractorService annotationExtractorService,
            com.codetutor.backend.analyzer.service.ImportAnalyzerService importAnalyzerService,
            com.codetutor.backend.ir.service.CodeIrGeneratorService codeIrGeneratorService,
            com.codetutor.backend.graph.service.DependencyGraphBuilderService dependencyGraphBuilderService,
            com.codetutor.backend.analyzer.service.ApiAnalyzerService apiAnalyzerService,
            com.codetutor.backend.analyzer.service.DatabaseAnalyzerService databaseAnalyzerService,
            com.codetutor.backend.analyzer.service.ModuleDetectorService moduleDetectorService,
            com.codetutor.backend.vector.service.VectorService vectorService,
            com.codetutor.backend.llm.service.ChatEngineService chatEngineService
    ) {
        this.projectService = projectService;
        this.fileScannerService = fileScannerService;
        this.technologyDetectorService = technologyDetectorService;
        this.javaParserService = javaParserService;
        this.codeStructureExtractorService = codeStructureExtractorService;
        this.annotationExtractorService = annotationExtractorService;
        this.importAnalyzerService = importAnalyzerService;
        this.codeIrGeneratorService = codeIrGeneratorService;
        this.dependencyGraphBuilderService = dependencyGraphBuilderService;
        this.apiAnalyzerService = apiAnalyzerService;
        this.databaseAnalyzerService = databaseAnalyzerService;
        this.moduleDetectorService = moduleDetectorService;
        this.vectorService = vectorService;
        this.chatEngineService = chatEngineService;
    }

    /**
     * Module 4: Create a project from a public GitHub repository.
     */
    @PostMapping("/github")
    public ResponseEntity<ApiResponse<ProjectResponse>> createFromGithub(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateGithubProjectRequest request
    ) {
        ProjectResponse response = projectService.createFromGithub(userDetails.getUsername(), request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("GitHub project created successfully", response));
    }

    /**
     * Module 5: Create a project by uploading a project ZIP file.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProjectResponse>> createFromZip(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("file") MultipartFile file
    ) {
        ProjectResponse response = projectService.createFromZip(
                userDetails.getUsername(), name, description, file
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("ZIP project uploaded and created successfully", response));
    }

    /**
     * Module 3: List all projects owned by the authenticated user.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> listProjects(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        List<ProjectResponse> projects = projectService.listProjects(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Projects retrieved successfully", projects));
    }

    /**
     * Module 3: Get details of a single project by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProject(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        ProjectResponse project = projectService.getProject(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.success("Project details retrieved", project));
    }

    /**
     * Module 3: Delete a project by ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProject(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        projectService.deleteProject(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.success("Project deleted successfully", null));
    }

    /**
     * Module 3: Get analysis status of a project (for status polling).
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ProjectResponse>> getAnalysisStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        ProjectResponse response = projectService.getAnalysisStatus(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.success("Project analysis status retrieved", response));
    }

    /**
     * Module 8: Get scanned file tree structure for project workspace.
     */
    @GetMapping("/{id}/files")
    public ResponseEntity<ApiResponse<ScanResult>> getProjectFiles(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        // Ensure user owns project
        projectService.getProject(userDetails.getUsername(), id);
        ScanResult scanResult = fileScannerService.scanWorkspace(id);
        return ResponseEntity.ok(ApiResponse.success("Workspace file tree retrieved", scanResult));
    }

    /**
     * Module 10: Get detected technology stack, language breakdown, and frameworks.
     */
    @GetMapping("/{id}/tech-stack")
    public ResponseEntity<ApiResponse<TechStackResult>> getTechStack(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        // Ensure user owns project
        projectService.getProject(userDetails.getUsername(), id);
        TechStackResult techStack = technologyDetectorService.detectTechStack(id);
        return ResponseEntity.ok(ApiResponse.success("Technology stack detected successfully", techStack));
    }

    /**
     * Module 11: Get parsed Java Abstract Syntax Trees (ASTs) for project workspace.
     */
    @GetMapping("/{id}/ast")
    public ResponseEntity<ApiResponse<List<JavaAstNode>>> getProjectJavaAst(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        // Ensure user owns project
        projectService.getProject(userDetails.getUsername(), id);
        List<JavaAstNode> astRoots = javaParserService.parseWorkspaceJavaFiles(id);
        return ResponseEntity.ok(ApiResponse.success("Java AST parsed successfully", astRoots));
    }

    /**
     * Module 12: Get extracted code structure (Classes, Interfaces, Enums, Methods, Fields, Modifiers).
     */
    @GetMapping("/{id}/structure")
    public ResponseEntity<ApiResponse<CodeStructureSummary>> getCodeStructure(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        // Ensure user owns project
        projectService.getProject(userDetails.getUsername(), id);
        CodeStructureSummary summary = codeStructureExtractorService.extractCodeStructure(id);
        return ResponseEntity.ok(ApiResponse.success("Code structure extracted successfully", summary));
    }

    /**
     * Module 13: Get detected annotations and architectural stereotype classification.
     */
    @GetMapping("/{id}/annotations")
    public ResponseEntity<ApiResponse<AnnotationSummary>> getProjectAnnotations(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        // Ensure user owns project
        projectService.getProject(userDetails.getUsername(), id);
        AnnotationSummary summary = annotationExtractorService.extractAnnotations(id);
        return ResponseEntity.ok(ApiResponse.success("Annotations extracted successfully", summary));
    }

    /**
     * Module 14: Get extracted import dependencies and analyze internal vs external usage.
     */
    @GetMapping("/{id}/imports")
    public ResponseEntity<ApiResponse<com.codetutor.backend.analyzer.dto.ImportDependencySummary>> getImportDependencies(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        // Ensure user owns project
        projectService.getProject(userDetails.getUsername(), id);
        com.codetutor.backend.analyzer.dto.ImportDependencySummary summary = importAnalyzerService.analyzeImports(id);
        return ResponseEntity.ok(ApiResponse.success("Import dependencies analyzed successfully", summary));
    }

    /**
     * Module 15: Get full Code IR (Intermediate Representation) aggregating all analysis data.
     */
    @GetMapping("/{id}/ir")
    public ResponseEntity<ApiResponse<com.codetutor.backend.ir.dto.ProjectCodeIR>> getProjectCodeIR(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        // Ensure user owns project
        projectService.getProject(userDetails.getUsername(), id);
        com.codetutor.backend.ir.dto.ProjectCodeIR ir = codeIrGeneratorService.generateProjectIR(id);
        return ResponseEntity.ok(ApiResponse.success("Project Code IR generated successfully", ir));
    }

    /**
     * Module 19: Get project dependency graph (nodes and edges) for architecture visualization.
     */
    @GetMapping("/{id}/graph")
    public ResponseEntity<ApiResponse<com.codetutor.backend.graph.dto.DependencyGraph>> getProjectGraph(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        // Ensure user owns project
        projectService.getProject(userDetails.getUsername(), id);
        com.codetutor.backend.graph.dto.DependencyGraph graph = dependencyGraphBuilderService.buildGraph(id);
        return ResponseEntity.ok(ApiResponse.success("Dependency graph generated successfully", graph));
    }

    /**
     * Module 21: Get REST API endpoints detected in the project.
     */
    @GetMapping("/{id}/apis")
    public ResponseEntity<ApiResponse<com.codetutor.backend.analyzer.dto.ApiEndpointSummary>> getProjectApis(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        projectService.getProject(userDetails.getUsername(), id);
        com.codetutor.backend.analyzer.dto.ApiEndpointSummary summary = apiAnalyzerService.analyzeApis(id);
        return ResponseEntity.ok(ApiResponse.success("API endpoints analyzed successfully", summary));
    }

    /**
     * Module 22: Get Database schema information (entities, tables, columns, relationships).
     */
    @GetMapping("/{id}/database")
    public ResponseEntity<ApiResponse<com.codetutor.backend.analyzer.dto.DatabaseSchemaSummary>> getProjectDatabase(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        projectService.getProject(userDetails.getUsername(), id);
        com.codetutor.backend.analyzer.dto.DatabaseSchemaSummary summary = databaseAnalyzerService.analyzeDatabase(id);
        return ResponseEntity.ok(ApiResponse.success("Database schema analyzed successfully", summary));
    }

    /**
     * Module 23: Get logical modules detected in the project based on packages.
     */
    @GetMapping("/{id}/modules")
    public ResponseEntity<ApiResponse<com.codetutor.backend.analyzer.dto.ModuleDetectionSummary>> getProjectModules(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        projectService.getProject(userDetails.getUsername(), id);
        com.codetutor.backend.analyzer.dto.ModuleDetectionSummary summary = moduleDetectorService.detectModules(id);
        return ResponseEntity.ok(ApiResponse.success("Logical modules detected successfully", summary));
    }

    /**
     * Module 24: Get raw file content for Monaco Editor.
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<String> getFileContent(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestParam String path
    ) {
        projectService.getProject(userDetails.getUsername(), id);
        String content = fileScannerService.getFileContent(id, path);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                .body(content);
    }

    /**
     * Module 28-30: Generate Vector Embeddings for Project IR.
     */
    @PostMapping("/{id}/embeddings")
    public ResponseEntity<ApiResponse<com.codetutor.backend.vector.dto.VectorEmbeddingSummary>> generateEmbeddings(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        projectService.getProject(userDetails.getUsername(), id);
        com.codetutor.backend.vector.dto.VectorEmbeddingSummary summary = vectorService.processEmbeddings(id);
        return ResponseEntity.ok(ApiResponse.success("Vector embeddings generated successfully", summary));
    }

    /**
     * Module 33: Chat Service API (AI RAG).
     */
    @PostMapping("/{id}/chat")
    public ResponseEntity<ApiResponse<com.codetutor.backend.llm.dto.ChatResponse>> chatWithProject(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestBody com.codetutor.backend.llm.dto.ChatRequest request
    ) {
        projectService.getProject(userDetails.getUsername(), id);
        com.codetutor.backend.llm.dto.ChatResponse response = chatEngineService.handleChat(id, request);
        return ResponseEntity.ok(ApiResponse.success("Chat response generated", response));
    }
}
