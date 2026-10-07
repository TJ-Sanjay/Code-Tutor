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

/**
 * Project Management REST controller.
 * Context Path: /api/projects
 *
 * Module 3: Project CRUD & List
 * Module 4: GitHub Project Creation
 * Module 5: ZIP File Upload Project Creation
 */
@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
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
}
