package com.codetutor.backend.detector.dto;

import java.util.Map;
import java.util.Set;

/**
 * Result DTO containing detected technologies, languages percentages, frameworks, and databases.
 */
public class TechStackResult {

    private String primaryLanguage;
    private Map<String, Double> languagePercentages;
    private Set<String> frameworks;
    private Set<String> buildTools;
    private Set<String> databases;
    private Set<String> libraries;

    public TechStackResult() {}

    public TechStackResult(
            String primaryLanguage,
            Map<String, Double> languagePercentages,
            Set<String> frameworks,
            Set<String> buildTools,
            Set<String> databases,
            Set<String> libraries
    ) {
        this.primaryLanguage = primaryLanguage;
        this.languagePercentages = languagePercentages;
        this.frameworks = frameworks;
        this.buildTools = buildTools;
        this.databases = databases;
        this.libraries = libraries;
    }

    public String getPrimaryLanguage() { return primaryLanguage; }
    public void setPrimaryLanguage(String primaryLanguage) { this.primaryLanguage = primaryLanguage; }

    public Map<String, Double> getLanguagePercentages() { return languagePercentages; }
    public void setLanguagePercentages(Map<String, Double> languagePercentages) { this.languagePercentages = languagePercentages; }

    public Set<String> getFrameworks() { return frameworks; }
    public void setFrameworks(Set<String> frameworks) { this.frameworks = frameworks; }

    public Set<String> getBuildTools() { return buildTools; }
    public void setBuildTools(Set<String> buildTools) { this.buildTools = buildTools; }

    public Set<String> getDatabases() { return databases; }
    public void setDatabases(Set<String> databases) { this.databases = databases; }

    public Set<String> getLibraries() { return libraries; }
    public void setLibraries(Set<String> libraries) { this.libraries = libraries; }
}
