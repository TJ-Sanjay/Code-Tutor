package com.codetutor.backend.detector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.codetutor.backend.detector.dto.TechStackResult;
import com.codetutor.backend.project.model.Project;
import com.codetutor.backend.project.repository.ProjectRepository;
import com.codetutor.backend.project.service.WorkspaceManagerService;
import com.codetutor.backend.scanner.service.FileScannerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Module 10 — Technology Detector Service.
 *
 * Inspects project workspace files (pom.xml, package.json, requirements.txt, build.gradle, README, etc.):
 *   1. Identifies programming languages and calculates usage percentage
 *   2. Identifies primary language (Java, JavaScript, Python, etc.)
 *   3. Detects frameworks (Spring Boot, React, Next.js, Express, Django, etc.)
 *   4. Detects build tools (Maven, Gradle, Vite, npm, etc.)
 *   5. Detects databases (PostgreSQL, H2, MySQL, MongoDB, Redis, etc.)
 *   6. Persists techStackJson and primaryLanguage to Project entity
 */
@Service
public class TechnologyDetectorService {

    private static final Logger log = LoggerFactory.getLogger(TechnologyDetectorService.class);

    private static final Map<String, String> EXTENSION_TO_LANGUAGE = Map.ofEntries(
            Map.entry("java", "Java"),
            Map.entry("js", "JavaScript"),
            Map.entry("jsx", "JavaScript (React)"),
            Map.entry("ts", "TypeScript"),
            Map.entry("tsx", "TypeScript (React)"),
            Map.entry("py", "Python"),
            Map.entry("html", "HTML"),
            Map.entry("css", "CSS"),
            Map.entry("scss", "SCSS"),
            Map.entry("sql", "SQL"),
            Map.entry("xml", "XML"),
            Map.entry("json", "JSON"),
            Map.entry("yml", "YAML"),
            Map.entry("yaml", "YAML"),
            Map.entry("md", "Markdown"),
            Map.entry("sh", "Shell"),
            Map.entry("bat", "Batch"),
            Map.entry("ps1", "PowerShell")
    );

    private final WorkspaceManagerService workspaceManagerService;
    private final FileScannerService fileScannerService;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;

    public TechnologyDetectorService(
            WorkspaceManagerService workspaceManagerService,
            FileScannerService fileScannerService,
            ProjectRepository projectRepository,
            ObjectMapper objectMapper
    ) {
        this.workspaceManagerService = workspaceManagerService;
        this.fileScannerService = fileScannerService;
        this.projectRepository = projectRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Detects technology stack for a project workspace and updates the Project entity.
     */
    @Transactional
    public TechStackResult detectTechStack(Long projectId) {
        Path workspaceDir = workspaceManagerService.getWorkspaceDir(projectId);
        List<String> flatFiles = fileScannerService.getFlatFileList(projectId);

        log.info("Starting technology detection for project id={} ({} files)", projectId, flatFiles.size());

        // 1. Language Breakdown
        Map<String, Integer> langCounts = new HashMap<>();
        int totalAnalyzedFiles = 0;

        for (String file : flatFiles) {
            int idx = file.lastIndexOf('.');
            if (idx > 0 && idx < file.length() - 1) {
                String ext = file.substring(idx + 1).toLowerCase();
                if (EXTENSION_TO_LANGUAGE.containsKey(ext)) {
                    String lang = EXTENSION_TO_LANGUAGE.get(ext);
                    langCounts.put(lang, langCounts.getOrDefault(lang, 0) + 1);
                    totalAnalyzedFiles++;
                }
            }
        }

        Map<String, Double> languagePercentages = new LinkedHashMap<>();
        String primaryLanguage = "Unknown";
        int maxCount = -1;

        if (totalAnalyzedFiles > 0) {
            List<Map.Entry<String, Integer>> sortedLangs = new ArrayList<>(langCounts.entrySet());
            sortedLangs.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

            for (Map.Entry<String, Integer> entry : sortedLangs) {
                double pct = Math.round((entry.getValue() * 100.0 / totalAnalyzedFiles) * 10.0) / 10.0;
                languagePercentages.put(entry.getKey(), pct);
                if (entry.getValue() > maxCount) {
                    maxCount = entry.getValue();
                    primaryLanguage = entry.getKey().split(" ")[0]; // e.g. "Java"
                }
            }
        }

        // 2. Frameworks, Build Tools, Databases Detection
        Set<String> frameworks = new LinkedHashSet<>();
        Set<String> buildTools = new LinkedHashSet<>();
        Set<String> databases = new LinkedHashSet<>();
        Set<String> libraries = new LinkedHashSet<>();

        inspectBuildConfigFiles(workspaceDir, frameworks, buildTools, databases, libraries);

        TechStackResult result = new TechStackResult(
                primaryLanguage,
                languagePercentages,
                frameworks,
                buildTools,
                databases,
                libraries
        );

        // 3. Persist to Project Entity
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            project.setPrimaryLanguage(primaryLanguage);
            try {
                String json = objectMapper.writeValueAsString(result);
                project.setTechStackJson(json);
            } catch (Exception e) {
                log.warn("Could not serialize tech stack JSON: {}", e.getMessage());
            }
            projectRepository.save(project);
            log.info("Project id={} updated with primary language: {} and detected frameworks: {}",
                    projectId, primaryLanguage, frameworks);
        }

        return result;
    }

    private void inspectBuildConfigFiles(
            Path workspaceDir,
            Set<String> frameworks,
            Set<String> buildTools,
            Set<String> databases,
            Set<String> libraries
    ) {
        try (Stream<Path> stream = Files.walk(workspaceDir, 4)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String fileName = path.getFileName().toString().toLowerCase();

                if (fileName.equals("pom.xml")) {
                    buildTools.add("Maven");
                    frameworks.add("Spring Boot");
                    readAndCheckContent(path, content -> {
                        if (content.contains("spring-boot-starter-security")) frameworks.add("Spring Security");
                        if (content.contains("spring-boot-starter-data-jpa")) frameworks.add("Spring Data JPA");
                        if (content.contains("postgresql")) databases.add("PostgreSQL");
                        if (content.contains("h2database") || content.contains("h2")) databases.add("H2");
                        if (content.contains("mysql")) databases.add("MySQL");
                        if (content.contains("jjwt") || content.contains("jsonwebtoken")) libraries.add("JWT Security");
                        if (content.contains("jgit")) libraries.add("JGit");
                    });
                } else if (fileName.equals("build.gradle") || fileName.equals("build.gradle.kts")) {
                    buildTools.add("Gradle");
                    frameworks.add("Spring Boot");
                } else if (fileName.equals("package.json")) {
                    buildTools.add("npm");
                    readAndCheckContent(path, content -> {
                        if (content.contains("\"react\"")) frameworks.add("React.js");
                        if (content.contains("\"next\"")) frameworks.add("Next.js");
                        if (content.contains("\"express\"")) frameworks.add("Express.js");
                        if (content.contains("\"vite\"")) buildTools.add("Vite");
                        if (content.contains("\"monaco-editor\"") || content.contains("@monaco-editor")) libraries.add("Monaco Editor");
                        if (content.contains("\"@xyflow/react\"") || content.contains("reactflow")) libraries.add("React Flow");
                    });
                } else if (fileName.equals("requirements.txt") || fileName.equals("pipfile") || fileName.equals("pyproject.toml")) {
                    buildTools.add("pip");
                    readAndCheckContent(path, content -> {
                        if (content.contains("django")) frameworks.add("Django");
                        if (content.contains("flask")) frameworks.add("Flask");
                        if (content.contains("fastapi")) frameworks.add("FastAPI");
                        if (content.contains("tree-sitter")) libraries.add("Tree-sitter");
                        if (content.contains("networkx")) libraries.add("NetworkX");
                    });
                }
            });
        } catch (IOException e) {
            log.warn("Error walking workspace config files: {}", e.getMessage());
        }
    }

    private void readAndCheckContent(Path path, java.util.function.Consumer<String> consumer) {
        try {
            String content = Files.readString(path).toLowerCase();
            consumer.accept(content);
        } catch (Exception e) {
            log.warn("Could not read file for tech detection {}: {}", path.getFileName(), e.getMessage());
        }
    }
}
