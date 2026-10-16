package com.codetutor.backend.project.service;

import com.codetutor.backend.project.model.Project;
import com.codetutor.backend.project.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/** Background executor for repository processing + start-up housekeeping. */
@Configuration
@EnableAsync
public class AppLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AppLifecycle.class);

    private final ProjectRepository projects;
    private final WorkspaceManagerService workspaces;

    public AppLifecycle(ProjectRepository projects, WorkspaceManagerService workspaces) {
        this.projects = projects;
        this.workspaces = workspaces;
    }

    @Bean(name = "analysisExecutor")
    public Executor analysisExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(3);
        ex.setQueueCapacity(30);
        ex.setThreadNamePrefix("analysis-");
        ex.initialize();
        return ex;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        // 1. projects that were mid-analysis when the server stopped can never finish
        List<Project> stuck = projects.findByStatusIn(List.of(Project.AnalysisStatus.QUEUED, Project.AnalysisStatus.CLONING,
                Project.AnalysisStatus.EXTRACTING, Project.AnalysisStatus.SCANNING, Project.AnalysisStatus.PARSING,
                Project.AnalysisStatus.ANALYZING, Project.AnalysisStatus.PENDING));
        for (Project p : stuck) {
            p.setStatus(Project.AnalysisStatus.FAILED);
            p.setStatusMessage("Interrupted by a server restart — press “Re-analyse” to try again.");
            projects.save(p);
        }
        if (!stuck.isEmpty()) log.info("Marked {} interrupted project(s) as failed", stuck.size());

        // 2. workspace folders without a project row are orphans (e.g. data from an older run)
        Set<String> known = projects.findAll().stream().map(p -> String.valueOf(p.getId())).collect(Collectors.toSet());
        for (Path dir : workspaces.listWorkspaceDirs()) {
            if (!known.contains(dir.getFileName().toString())) {
                log.info("Removing orphan workspace {}", dir);
                workspaces.deleteTree(dir);
            }
        }
    }
}
