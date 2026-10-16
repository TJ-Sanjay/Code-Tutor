package com.codetutor.backend.project.service;

import com.codetutor.backend.analysis.AnalysisStore;
import com.codetutor.backend.analysis.Knowledge;
import com.codetutor.backend.auth.model.User;
import com.codetutor.backend.auth.repository.UserRepository;
import com.codetutor.backend.common.exception.NotFoundException;
import com.codetutor.backend.common.exception.NotReadyException;
import com.codetutor.backend.project.model.Project;
import com.codetutor.backend.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * The single gate every project endpoint goes through. A project that does not exist and a project owned by
 * somebody else are indistinguishable to the caller (both → 404), so users can never see or probe other accounts' data.
 */
@Service
public class ProjectAccess {

    public record Ctx(Project project, User user, Knowledge k, Path root) {}

    private final ProjectRepository projects;
    private final UserRepository users;
    private final AnalysisStore store;
    private final WorkspaceManagerService workspaces;

    public ProjectAccess(ProjectRepository projects, UserRepository users, AnalysisStore store, WorkspaceManagerService workspaces) {
        this.projects = projects;
        this.users = users;
        this.store = store;
        this.workspaces = workspaces;
    }

    public User user(String email) {
        return users.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
    }

    public Project owned(String email, long projectId) {
        return projects.findByIdAndOwnerEmail(projectId, email).orElseThrow(() -> new NotFoundException("Project not found"));
    }

    /** Owned + analysed: returns everything needed to serve analysis data. */
    public Ctx ready(String email, long projectId) {
        Project p = owned(email, projectId);
        if (p.getStatus() != Project.AnalysisStatus.COMPLETED) {
            if (p.getStatus() == Project.AnalysisStatus.FAILED) throw new NotReadyException("Analysis failed: " + p.getStatusMessage());
            throw new NotReadyException("This project is still being analysed (" + p.getProgressPercent() + "%). Please wait a moment.");
        }
        Knowledge k = store.load(projectId).orElseThrow(() -> new NotReadyException("Analysis data is missing — please re-analyse the project."));
        return new Ctx(p, user(email), k, workspaces.getWorkspaceDir(projectId));
    }
}
