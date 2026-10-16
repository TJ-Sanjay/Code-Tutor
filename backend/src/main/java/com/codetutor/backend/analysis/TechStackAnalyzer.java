package com.codetutor.backend.analysis;

import com.codetutor.backend.analysis.model.AnalysisModel.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads pom.xml / build.gradle / package.json / requirements.txt, Docker & CI files and detects the stack. */
public class TechStackAnalyzer {

    public record Result(List<Library> libraries, List<String> frameworks, List<String> buildTools,
                         List<String> databases, List<String> tools, Map<String, Double> languages,
                         String contextPath) {}

    private static final Map<String, String> LANG_BY_EXT = new LinkedHashMap<>();
    static {
        LANG_BY_EXT.put("java", "Java");
        LANG_BY_EXT.put("kt", "Kotlin");
        LANG_BY_EXT.put("js", "JavaScript");
        LANG_BY_EXT.put("jsx", "JavaScript");
        LANG_BY_EXT.put("mjs", "JavaScript");
        LANG_BY_EXT.put("ts", "TypeScript");
        LANG_BY_EXT.put("tsx", "TypeScript");
        LANG_BY_EXT.put("py", "Python");
        LANG_BY_EXT.put("html", "HTML");
        LANG_BY_EXT.put("css", "CSS");
        LANG_BY_EXT.put("scss", "CSS");
        LANG_BY_EXT.put("sql", "SQL");
        LANG_BY_EXT.put("xml", "XML");
        LANG_BY_EXT.put("yml", "YAML");
        LANG_BY_EXT.put("yaml", "YAML");
        LANG_BY_EXT.put("properties", "Properties");
        LANG_BY_EXT.put("sh", "Shell");
        LANG_BY_EXT.put("go", "Go");
        LANG_BY_EXT.put("cs", "C#");
        LANG_BY_EXT.put("rb", "Ruby");
        LANG_BY_EXT.put("php", "PHP");
    }

    private static final Set<String> PROGRAMMING = Set.of("Java", "Kotlin", "JavaScript", "TypeScript", "Python", "Go", "C#",
            "Ruby", "PHP", "Shell");

    /** The dominant programming language (not markup / config) from a language-share map. */
    public static String primaryLanguage(Map<String, Double> languages) {
        for (String l : languages.keySet()) if (PROGRAMMING.contains(l)) return l;
        return languages.isEmpty() ? "Unknown" : languages.keySet().iterator().next();
    }

    private final ObjectMapper mapper = new ObjectMapper();

    public Result analyze(Path root, List<FileInfo> files) {
        List<Library> libs = new ArrayList<>();
        Set<String> frameworks = new LinkedHashSet<>();
        Set<String> build = new LinkedHashSet<>();
        Set<String> dbs = new LinkedHashSet<>();
        Set<String> tools = new LinkedHashSet<>();
        String contextPath = "";

        for (FileInfo f : files) {
            String p = f.path();
            String name = p.substring(p.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
            try {
                switch (name) {
                    case "pom.xml" -> { build.add("Maven"); readPom(root.resolve(p), libs); }
                    case "build.gradle", "build.gradle.kts" -> { build.add("Gradle"); readGradle(root.resolve(p), libs); }
                    case "package.json" -> { build.add("npm"); readPackageJson(root.resolve(p), libs); }
                    case "requirements.txt" -> { build.add("pip"); readRequirements(root.resolve(p), libs); }
                    case "dockerfile" -> tools.add("Docker");
                    case "docker-compose.yml", "docker-compose.yaml", "compose.yaml" -> { tools.add("Docker"); tools.add("Docker Compose"); }
                    case "vite.config.js", "vite.config.ts" -> build.add("Vite");
                    case "application.yml", "application.yaml" -> contextPath = firstNonEmpty(contextPath, yamlContextPath(root.resolve(p)));
                    case "application.properties" -> contextPath = firstNonEmpty(contextPath, propContextPath(root.resolve(p)));
                    default -> { }
                }
            } catch (IOException | RuntimeException ignored) {
                // unreadable build files must never fail the whole analysis
            }
            if (p.startsWith(".github/workflows/")) tools.add("GitHub Actions");
            if (p.startsWith("k8s/") || p.contains("/k8s/") || p.startsWith("kubernetes/")) tools.add("Kubernetes");
            if (p.endsWith("Jenkinsfile")) tools.add("Jenkins");
        }

        for (Library l : libs) {
            String n = l.name().toLowerCase(Locale.ROOT);
            if (n.contains("spring-boot")) frameworks.add("Spring Boot");
            if (n.contains("spring-boot-starter-web") || n.contains("spring-webmvc")) frameworks.add("Spring MVC");
            if (n.contains("spring-boot-starter-security") || n.contains("spring-security")) frameworks.add("Spring Security");
            if (n.contains("data-jpa") || n.contains("hibernate")) frameworks.add("Spring Data JPA / Hibernate");
            if (n.contains("thymeleaf")) frameworks.add("Thymeleaf");
            if (n.contains("spring-boot-starter-webflux")) frameworks.add("Spring WebFlux");
            if (n.equals("react") || n.equals("react-dom")) frameworks.add("React");
            if (n.equals("next")) frameworks.add("Next.js");
            if (n.equals("vue")) frameworks.add("Vue");
            if (n.equals("@angular/core")) frameworks.add("Angular");
            if (n.equals("express")) frameworks.add("Express");
            if (n.equals("django")) frameworks.add("Django");
            if (n.equals("flask")) frameworks.add("Flask");
            if (n.equals("fastapi")) frameworks.add("FastAPI");
            if (n.contains("postgresql")) dbs.add("PostgreSQL");
            if (n.contains("com.h2database") || n.equals("h2")) dbs.add("H2");
            if (n.contains("mysql")) dbs.add("MySQL");
            if (n.contains("mongodb")) dbs.add("MongoDB");
            if (n.contains("redis")) dbs.add("Redis");
            if (n.contains("mariadb")) dbs.add("MariaDB");
            if (n.contains("jjwt") || n.contains("jsonwebtoken") || n.contains("java-jwt")) tools.add("JWT");
            if (n.contains("lombok")) tools.add("Lombok");
            if (n.contains("flyway")) tools.add("Flyway");
            if (n.contains("liquibase")) tools.add("Liquibase");
            if (n.contains("springdoc") || n.contains("swagger")) tools.add("OpenAPI / Swagger");
            if (n.contains("jgit")) tools.add("JGit");
            if (n.contains("javaparser")) tools.add("JavaParser");
            if (n.contains("actuator")) tools.add("Spring Actuator");
            if (n.contains("kafka")) tools.add("Kafka");
            if (n.contains("rabbit")) tools.add("RabbitMQ");
            if (n.equals("vite")) build.add("Vite");
            if (n.equals("tailwindcss")) tools.add("Tailwind CSS");
            if (n.equals("@xyflow/react") || n.equals("reactflow")) tools.add("React Flow");
            if (n.contains("monaco")) tools.add("Monaco Editor");
            if (n.equals("axios")) tools.add("Axios");
            if (n.equals("redux") || n.equals("@reduxjs/toolkit")) tools.add("Redux");
            if (n.contains("junit") || n.equals("jest") || n.equals("vitest")) tools.add("Testing");
        }

        // languages by lines of code
        // share of lines of code per language. Generated / vendored markup (bundled CSS, big JSON …) is capped per
        // file so it cannot drown out the real source code.
        Map<String, Long> loc = new HashMap<>();
        long total = 0;
        for (FileInfo f : files) {
            String lang = LANG_BY_EXT.get(ext(f.path()));
            if (lang == null) continue;
            long lines = Math.max(f.lines(), 1);
            if (!PROGRAMMING.contains(lang)) lines = Math.min(lines, 400);
            loc.merge(lang, lines, Long::sum);
            total += lines;
        }
        Map<String, Double> pct = new LinkedHashMap<>();
        if (total > 0) {
            final long tot = total;
            loc.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .forEach(e -> pct.put(e.getKey(), Math.round(e.getValue() * 1000.0 / tot) / 10.0));
        }
        return new Result(libs, new ArrayList<>(frameworks), new ArrayList<>(build), new ArrayList<>(dbs),
                new ArrayList<>(tools), pct, contextPath);
    }

    static String ext(String path) {
        int i = path.lastIndexOf('.');
        return i < 0 ? "" : path.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private static String firstNonEmpty(String a, String b) { return a != null && !a.isEmpty() ? a : (b == null ? "" : b); }

    private static final Pattern DEP = Pattern.compile(
            "<dependency>(.*?)</dependency>", Pattern.DOTALL);

    private void readPom(Path p, List<Library> libs) throws IOException {
        String xml = Files.readString(p);
        String noMgmt = xml.replaceAll("(?s)<dependencyManagement>.*?</dependencyManagement>", "");
        Matcher m = DEP.matcher(noMgmt);
        while (m.find()) {
            String b = m.group(1);
            String g = tag(b, "groupId");
            String a = tag(b, "artifactId");
            String v = tag(b, "version");
            String s = tag(b, "scope");
            if (a != null) libs.add(new Library((g != null ? g + ":" : "") + a, v == null ? "managed" : v,
                    s == null ? "compile" : s, "maven"));
        }
        if (xml.contains("spring-boot-starter-parent")) {
            Matcher pm = Pattern.compile("<parent>.*?<version>([^<]+)</version>", Pattern.DOTALL).matcher(xml);
            libs.add(new Library("org.springframework.boot:spring-boot-starter-parent", pm.find() ? pm.group(1) : "?",
                    "parent", "maven"));
        }
    }

    private static String tag(String s, String t) {
        Matcher m = Pattern.compile("<" + t + ">\\s*([^<]*?)\\s*</" + t + ">").matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private void readGradle(Path p, List<Library> libs) throws IOException {
        String t = Files.readString(p);
        Matcher m = Pattern.compile("(implementation|api|runtimeOnly|compileOnly|testImplementation)\\s*\\(?\\s*['\"]([^'\"]+)['\"]")
                .matcher(t);
        while (m.find()) {
            String[] parts = m.group(2).split(":");
            String name = parts.length >= 2 ? parts[0] + ":" + parts[1] : m.group(2);
            libs.add(new Library(name, parts.length >= 3 ? parts[2] : "managed", m.group(1), "gradle"));
        }
        if (t.contains("org.springframework.boot")) libs.add(new Library("org.springframework.boot:spring-boot", "?", "plugin", "gradle"));
    }

    private void readPackageJson(Path p, List<Library> libs) throws IOException {
        JsonNode n = mapper.readTree(Files.readString(p));
        for (String sec : List.of("dependencies", "devDependencies")) {
            JsonNode d = n.get(sec);
            if (d == null) continue;
            d.fields().forEachRemaining(e -> libs.add(new Library(e.getKey(), e.getValue().asText(),
                    sec.equals("dependencies") ? "runtime" : "dev", "npm")));
        }
    }

    private void readRequirements(Path p, List<Library> libs) throws IOException {
        for (String line : Files.readAllLines(p)) {
            String l = line.trim();
            if (l.isEmpty() || l.startsWith("#")) continue;
            String[] parts = l.split("[=<>~!]=?", 2);
            libs.add(new Library(parts[0].trim().toLowerCase(Locale.ROOT), parts.length > 1 ? parts[1].trim() : "any",
                    "runtime", "pip"));
        }
    }

    private String yamlContextPath(Path p) throws IOException {
        // look for  server: / servlet: / context-path: /x   (indentation based, good enough for typical files)
        List<String> lines = Files.readAllLines(p);
        boolean server = false, servlet = false;
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("#") || t.isEmpty()) continue;
            if (!line.startsWith(" ") && !line.startsWith("\t")) { server = t.startsWith("server:"); servlet = false; continue; }
            if (server && t.startsWith("servlet:")) servlet = true;
            if (server && servlet && t.startsWith("context-path:")) return t.substring("context-path:".length()).trim();
            if (server && t.startsWith("servlet.context-path:")) return t.substring("servlet.context-path:".length()).trim();
        }
        return "";
    }

    private String propContextPath(Path p) throws IOException {
        for (String line : Files.readAllLines(p)) {
            String t = line.trim();
            if (t.startsWith("server.servlet.context-path")) {
                int i = t.indexOf('=');
                if (i > 0) return t.substring(i + 1).trim();
            }
        }
        return "";
    }
}
