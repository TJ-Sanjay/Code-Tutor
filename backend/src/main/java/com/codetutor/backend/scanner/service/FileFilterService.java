package com.codetutor.backend.scanner.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Set;

/**
 * Module 9 — File Filtering Service.
 *
 * Enforces repository scanning and analysis exclusion rules:
 *   1. Ignores VCS directories (.git, .idea, .vscode)
 *   2. Ignores build output & dependency folders (node_modules, target, build, dist, bin, __pycache__, .venv)
 *   3. Ignores secret/environment configuration files (.env, .env.local)
 *   4. Ignores compiled binaries, archives, images and OS metadata (.class, .jar, .zip, .DS_Store)
 */
@Service
public class FileFilterService {

    private static final Logger log = LoggerFactory.getLogger(FileFilterService.class);

    // Standard directories to exclude from scanning and code analysis
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            ".git", ".svn", ".hg", ".idea", ".vscode", ".settings", ".eclipse",
            "node_modules", "target", "build", "dist", "bin", "out",
            ".gradle", ".mvn", "vendor", "__pycache__", ".pytest_cache",
            ".venv", "env", "venv", ".next", ".nuxt", "coverage"
    );

    // Standard file names to exclude (secrets, OS files, lock files)
    private static final Set<String> IGNORED_EXACT_FILENAMES = Set.of(
            ".env", ".env.local", ".env.development", ".env.production", ".env.test",
            ".ds_store", "thumbs.db", "desktop.ini"
    );

    // Binary / non-text file extensions to exclude from code parsing
    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            "class", "jar", "war", "ear", "exe", "dll", "so", "dylib", "o", "a",
            "pyc", "pyo", "pyd", "zip", "tar", "gz", "7z", "rar", "iso",
            "png", "jpg", "jpeg", "gif", "ico", "bmp", "tiff", "webp", "svg",
            "mp3", "mp4", "avi", "mov", "pdf", "woff", "woff2", "ttf", "eot"
    );

    /**
     * Checks if a directory name should be excluded from scanning.
     */
    public boolean isIgnoredDirectory(String dirName) {
        if (dirName == null || dirName.isBlank()) return false;
        return IGNORED_DIRECTORIES.contains(dirName.toLowerCase());
    }

    /**
     * Checks if a file should be excluded based on name or extension.
     */
    public boolean isIgnoredFile(String fileName) {
        if (fileName == null || fileName.isBlank()) return false;

        String lowerName = fileName.toLowerCase();

        // Exact match check (.env, .DS_Store)
        if (IGNORED_EXACT_FILENAMES.contains(lowerName)) {
            return true;
        }

        // Extension check (.class, .png, .zip)
        int extIdx = lowerName.lastIndexOf('.');
        if (extIdx > 0 && extIdx < lowerName.length() - 1) {
            String ext = lowerName.substring(extIdx + 1);
            if (IGNORED_EXTENSIONS.contains(ext)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines whether a given path (file or directory) should be included in project analysis.
     */
    public boolean shouldIncludePath(Path currentPath, boolean isDirectory) {
        if (currentPath == null || currentPath.getFileName() == null) return false;
        String name = currentPath.getFileName().toString();

        if (isDirectory) {
            return !isIgnoredDirectory(name);
        } else {
            return !isIgnoredFile(name);
        }
    }
}
