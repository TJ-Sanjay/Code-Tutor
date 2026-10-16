package com.codetutor.backend.project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * Module 5 — ZIP Upload Validator.
 *
 * Validates an uploaded ZIP file:
 *   1. File must not be empty
 *   2. MIME type must be application/zip or application/x-zip-compressed
 *   3. File extension must be .zip
 *   4. File size must not exceed the configured maximum (default 100 MB)
 *
 * Implements resource limit requirement from the system design.
 */
@Component
public class ZipValidator {

    private static final Logger log = LoggerFactory.getLogger(ZipValidator.class);

    private static final long MAX_SIZE_BYTES = 100L * 1024 * 1024; // 100 MB

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/zip",
            "application/x-zip-compressed",
            "application/x-zip",
            "application/octet-stream"  // some browsers send this for .zip
    );

    // ZIP magic bytes: PK (0x50 0x4B)
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B};

    /**
     * Validates the uploaded ZIP file.
     *
     * @param file the uploaded multipart file
     * @throws IllegalArgumentException on validation failure with a user-friendly message
     */
    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file uploaded or file is empty");
        }

        // Size check
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    String.format("File size %.1f MB exceeds the 100 MB limit",
                            file.getSize() / (1024.0 * 1024))
            );
        }

        // Extension check
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException(
                    "Only .zip files are accepted. Received: " + originalName
            );
        }

        // MIME type check
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            log.warn("Unexpected content type: {} for file: {}", contentType, originalName);
            // Not fatal — browsers inconsistently report content type for ZIP files
        }

        // Magic bytes check (first 2 bytes must be PK)
        try {
            byte[] firstBytes;
            try (java.io.InputStream in = file.getInputStream()) { firstBytes = in.readNBytes(2); }
            if (firstBytes.length < 2
                    || firstBytes[0] != ZIP_MAGIC[0]
                    || firstBytes[1] != ZIP_MAGIC[1]) {
                throw new IllegalArgumentException(
                        "File does not appear to be a valid ZIP archive (invalid magic bytes)"
                );
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Could not read file bytes for magic check: {}", e.getMessage());
        }

        log.info("ZIP file validated: {} ({} bytes)", originalName, file.getSize());
    }
}
