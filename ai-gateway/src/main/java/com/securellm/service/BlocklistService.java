package com.securellm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Checks prompts against a configurable word/phrase blocklist.
 *
 * Words are matched case-insensitively as whole substrings (not whole-word only),
 * so "bomb" blocks "bomb", "bombing", "car bomb", etc.
 *
 * Configure via application.yaml or environment variable:
 *
 *   gateway:
 *     blocklist:
 *       enabled: true
 *       words: "word1,word2,phrase one,phrase two"
 *
 * Or as an env var:
 *   BLOCKLIST_WORDS="word1,word2,phrase one"
 */
@Service
public class BlocklistService {

    private static final Logger log = LoggerFactory.getLogger(BlocklistService.class);

    private final boolean enabled;
    private final List<String> blockedTerms;

    public BlocklistService(
            @Value("${gateway.blocklist.enabled:true}") boolean enabled,
            @Value("${gateway.blocklist.words:}") String wordsConfig) {

        this.enabled = enabled;
        this.blockedTerms = Arrays.stream(wordsConfig.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(String::toLowerCase)
            .toList();

        if (!blockedTerms.isEmpty()) {
            log.info("Blocklist active with {} term(s)", blockedTerms.size());
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
