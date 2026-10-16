package com.codetutor.backend.project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Module 4 — GitHub Repository Validator.
 *
 * Validates that a given URL:
 *   1. Matches the GitHub URL pattern (https://github.com/owner/repo)
 *   2. Exists as a public repository (HTTP HEAD check)
 *
 * Extracts the canonical "owner/repo" name for internal use.
 */
@Component
public class GitHubValidator {

    private static final Logger log = LoggerFactory.getLogger(GitHubValidator.class);

    // Captures: group(1) = owner, group(2) = repo
    private static final Pattern GITHUB_PATTERN =
            Pattern.compile("^https://github\\.com/([\\w.\\-]+)/([\\w.\\-]+?)(?:\\.git)?(?:/.*)?$");

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS    = 5_000;

    /**
     * Validates the URL format and optionally checks reachability.
     *
     * @throws IllegalArgumentException if invalid or unreachable
     */
    public GitHubRepoInfo validateAndExtract(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("GitHub URL must not be blank");
        }

        String url = rawUrl.trim();
        Matcher m = GITHUB_PATTERN.matcher(url);
        if (!m.matches()) {
            throw new IllegalArgumentException(
                    "Invalid GitHub URL. Expected format: https://github.com/owner/repo"
            );
        }

        String owner = m.group(1);
        String repo  = m.group(2);
        String canonicalUrl = "https://github.com/" + owner + "/" + repo;

        // Verify repository exists via HTTP HEAD
        verifyRepositoryExists(canonicalUrl, owner, repo);

        return new GitHubRepoInfo(owner, repo, canonicalUrl);
    }

    private void verifyRepositoryExists(String canonicalUrl, String owner, String repo) {
        try {
            URL url = URI.create(canonicalUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "CodeTutor/1.0");

            int status = conn.getResponseCode();
            conn.disconnect();

            if (status == 404) {
                throw new IllegalArgumentException(
                        "GitHub repository not found: " + owner + "/" + repo +
                        ". Please check the URL and ensure the repository is public."
                );
            }
            if (status >= 400) {
                throw new IllegalArgumentException(
                        "Cannot access GitHub repository (HTTP " + status + "): " + canonicalUrl
                );
            }

            log.info("GitHub repo validated: {} (HTTP {})", canonicalUrl, status);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Could not reach GitHub to validate URL: {} — {}", canonicalUrl, e.getMessage());
            // Don't block the user if GitHub is unreachable — we'll fail at clone time
        }
    }

    /**
     * Immutable result of a successful validation.
     */
    public record GitHubRepoInfo(String owner, String repo, String canonicalUrl) {
        public String repositoryName() { return owner + "/" + repo; }
        public String cloneUrl() { return canonicalUrl + ".git"; }
    }
}
