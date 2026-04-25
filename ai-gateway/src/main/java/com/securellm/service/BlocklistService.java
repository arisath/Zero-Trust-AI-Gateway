package com.securellm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Checks prompts against a configurable word/phrase blocklist loaded from a file on disk.
 *
 * Words are matched case-insensitively as whole substrings (not whole-word only),
 * so "bomb" blocks "bomb", "bombing", "car bomb", etc.
 *
 * File format: one term per line; blank lines and lines starting with '#' are ignored.
 *
 * Configure via application.yaml or environment variable:
 *
 *   gateway:
 *     blocklist:
 *       enabled: true
 *       file: /etc/ai-gateway/blocklist.txt   # optional — falls back to bundled blocklist.txt
 *
 * Or as an env var:
 *   BLOCKLIST_FILE=/etc/ai-gateway/blocklist.txt
 */
@Service
public class BlocklistService {

    private static final Logger log = LoggerFactory.getLogger(BlocklistService.class);

    private final boolean enabled;
    private final List<String> blockedTerms;

    @Autowired
    public BlocklistService(
            @Value("${gateway.blocklist.enabled:true}") boolean enabled,
            @Value("${gateway.blocklist.file:}") String filePath) {

        this.enabled = enabled;
        this.blockedTerms = loadFromFile(filePath);
    }

    /** Secondary constructor used by tests to inject terms directly (avoids file I/O). */
    public BlocklistService(boolean enabled, List<String> terms) {
        this.enabled = enabled;
        this.blockedTerms = terms.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .toList();
    }

    private static List<String> loadFromFile(String filePath) {
        if (filePath != null && !filePath.isBlank()) {
            Path path = Path.of(filePath);
            if (Files.exists(path)) {
                try {
                    List<String> terms = Files.lines(path)
                            .map(String::trim)
                            .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                            .map(String::toLowerCase)
                            .toList();
                    log.info("Blocklist loaded {} term(s) from {}", terms.size(), path);
                    return terms;
                } catch (IOException e) {
                    log.error("Failed to read blocklist file: {} — falling back to default", path, e);
                }
            } else {
                log.warn("Blocklist file not found: {} — falling back to default", path);
            }
        }
        return loadFromClasspath();
    }

    private static List<String> loadFromClasspath() {
        ClassPathResource resource = new ClassPathResource("blocklist.txt");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            List<String> terms = reader.lines()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                    .map(String::toLowerCase)
                    .toList();
            log.info("Blocklist loaded {} term(s) from bundled default", terms.size());
            return terms;
        } catch (IOException e) {
            log.error("Failed to read bundled blocklist — blocklist will be empty", e);
            return List.of();
        }
    }

    /**
     * Returns {@code true} if the prompt contains any blocked term.
     */
    public boolean isBlocked(String prompt) {
        if (!enabled || prompt == null || prompt.isBlank() || blockedTerms.isEmpty()) {
            return false;
        }
        String lower = prompt.toLowerCase();
        return blockedTerms.stream().anyMatch(lower::contains);
    }

    /**
     * Returns the first blocked term found in the prompt, or {@code null} if none.
     * Useful for logging which term triggered the block.
     */
    public String matchedTerm(String prompt) {
        if (prompt == null) return null;
        String lower = prompt.toLowerCase();
        return blockedTerms.stream().filter(lower::contains).findFirst().orElse(null);
    }
}
