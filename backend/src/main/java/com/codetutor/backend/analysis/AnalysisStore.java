package com.codetutor.backend.analysis;

import com.codetutor.backend.analysis.model.AnalysisModel.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Persists each project's analysis as gzip-compressed JSON on disk and keeps the hot ones in memory. */
@Service
public class AnalysisStore {

    private static final Logger log = LoggerFactory.getLogger(AnalysisStore.class);
    private static final int CACHE_SIZE = 6;

    private final Path dir;
    private final ObjectMapper mapper;
    private final Map<Long, Knowledge> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Knowledge> eldest) { return size() > CACHE_SIZE; }
    };

    public AnalysisStore(@Value("${app.data-dir:./data}") String dataDir, ObjectMapper mapper) throws IOException {
        this.dir = Paths.get(dataDir).toAbsolutePath().normalize().resolve("analysis");
        this.mapper = mapper;
        Files.createDirectories(dir);
    }

    private Path file(long projectId) { return dir.resolve(projectId + ".json.gz"); }

    public synchronized void save(long projectId, Result result) {
        try (OutputStream os = new GZIPOutputStream(Files.newOutputStream(file(projectId)))) {
            mapper.writeValue(os, result);
            cache.put(projectId, new Knowledge(result));
        } catch (IOException e) {
            throw new IllegalStateException("Could not save analysis: " + e.getMessage(), e);
        }
    }

    public synchronized Optional<Knowledge> load(long projectId) {
        Knowledge k = cache.get(projectId);
        if (k != null) return Optional.of(k);
        Path f = file(projectId);
        if (!Files.exists(f)) return Optional.empty();
        try (InputStream is = new GZIPInputStream(Files.newInputStream(f))) {
            Result r = mapper.readValue(is, Result.class);
            k = new Knowledge(r);
            cache.put(projectId, k);
            return Optional.of(k);
        } catch (IOException e) {
            log.error("Could not read analysis for project {}: {}", projectId, e.getMessage());
            return Optional.empty();
        }
    }

    public synchronized void delete(long projectId) {
        cache.remove(projectId);
        try {
            Files.deleteIfExists(file(projectId));
        } catch (IOException e) {
            log.warn("Could not delete analysis file for project {}", projectId);
        }
    }

    public synchronized void evict(long projectId) { cache.remove(projectId); }
}
