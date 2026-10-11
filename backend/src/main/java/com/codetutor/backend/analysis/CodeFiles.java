package com.codetutor.backend.analysis;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** File-type helpers shared by the scanner, the chunk builder and the file API. */
public final class CodeFiles {

    private CodeFiles() {}

    private static final Map<String, String> LANG = Map.ofEntries(
            Map.entry("java", "Java"), Map.entry("kt", "Kotlin"), Map.entry("js", "JavaScript"), Map.entry("jsx", "JavaScript"),
            Map.entry("mjs", "JavaScript"), Map.entry("ts", "TypeScript"), Map.entry("tsx", "TypeScript"), Map.entry("py", "Python"),
            Map.entry("html", "HTML"), Map.entry("css", "CSS"), Map.entry("scss", "CSS"), Map.entry("sql", "SQL"),
            Map.entry("xml", "XML"), Map.entry("yml", "YAML"), Map.entry("yaml", "YAML"), Map.entry("properties", "Properties"),
            Map.entry("json", "JSON"), Map.entry("md", "Markdown"), Map.entry("sh", "Shell"), Map.entry("gradle", "Gradle"),
            Map.entry("txt", "Text"), Map.entry("go", "Go"), Map.entry("cs", "C#"), Map.entry("rb", "Ruby"), Map.entry("php", "PHP"));

    private static final Set<String> SOURCE = Set.of("java", "kt", "js", "jsx", "mjs", "ts", "tsx", "py", "go", "cs", "rb", "php");

    public static String ext(String path) {
        int s = path.lastIndexOf('/');
        int i = path.lastIndexOf('.');
        return i < 0 || i < s ? "" : path.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    public static String language(String path) {
        String l = LANG.get(ext(path));
        if (l != null) return l;
        String name = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (name.equals("dockerfile")) return "Docker";
        return "Other";
    }

    public static boolean isSource(String path) { return SOURCE.contains(ext(path)); }

    public static boolean textual(String path) {
        String e = ext(path);
        if (LANG.containsKey(e)) return true;
        String name = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        return Set.of("dockerfile", "makefile", "jenkinsfile", ".gitignore", ".gitattributes", ".editorconfig", "mvnw", "license",
                "readme", "procfile").contains(name) || e.equals("toml") || e.equals("conf") || e.equals("cfg") || e.equals("ini")
                || e.equals("bat") || e.equals("cmd") || e.equals("ps1") || e.equals("lock") && false;
    }
}
