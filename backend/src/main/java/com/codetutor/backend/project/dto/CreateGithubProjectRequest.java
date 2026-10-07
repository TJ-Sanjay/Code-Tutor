package com.codetutor.backend.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a project from a GitHub URL (Module 4).
 */
public class CreateGithubProjectRequest {

    @NotBlank(message = "Project name is required")
    @Size(min = 1, max = 100, message = "Project name must be 1–100 characters")
    private String name;

    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;

    @NotBlank(message = "GitHub URL is required")
    @Pattern(
        regexp = "^https://github\\.com/[\\w.-]+/[\\w.-]+(/.*)?$",
        message = "Must be a valid GitHub repository URL (https://github.com/owner/repo)"
    )
    private String githubUrl;

    public CreateGithubProjectRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getGithubUrl() { return githubUrl; }
    public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }
}
