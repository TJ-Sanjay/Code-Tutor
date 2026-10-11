package com.codetutor.backend.analyzer.dto;

import java.util.List;

public class SpringComponentsSummary {

    private Long projectId;
    private List<String> controllers;
    private List<String> services;
    private List<String> repositories;
    private List<String> entities;
    private List<String> configurations;
    private List<String> components;

    public SpringComponentsSummary() {
    }

    public SpringComponentsSummary(Long projectId, List<String> controllers, List<String> services,
                                   List<String> repositories, List<String> entities,
                                   List<String> configurations, List<String> components) {
        this.projectId = projectId;
        this.controllers = controllers;
        this.services = services;
        this.repositories = repositories;
        this.entities = entities;
        this.configurations = configurations;
        this.components = components;
    }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public List<String> getControllers() { return controllers; }
    public void setControllers(List<String> controllers) { this.controllers = controllers; }

    public List<String> getServices() { return services; }
    public void setServices(List<String> services) { this.services = services; }

    public List<String> getRepositories() { return repositories; }
    public void setRepositories(List<String> repositories) { this.repositories = repositories; }

    public List<String> getEntities() { return entities; }
    public void setEntities(List<String> entities) { this.entities = entities; }

    public List<String> getConfigurations() { return configurations; }
    public void setConfigurations(List<String> configurations) { this.configurations = configurations; }

    public List<String> getComponents() { return components; }
    public void setComponents(List<String> components) { this.components = components; }
}
