package com.securellm.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Heuristic jailbreak / prompt-injection detection service.
 *
 * Uses a curated set of regex patterns to catch the most common attack vectors:
 * instruction-override phrases, role-play manipulation, model-token injection
 * (e.g. raw [INST] / im_start tags), and DAN-style prompts.
 *
 * This is a best-effort first line of defence. For higher assurance, replace or
 * augment with a dedicated classification model.
 */
@Service
public class JailbreakDetectionService {

    private static final List<Pattern> PATTERNS = List.of(
        // Instruction-override attacks
        Pattern.compile(
            "ignore\\s+(all\\s+)?(previous|prior|above|earlier)\\s+instructions?",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile(
            "disregard\\s+(your\\s+)?(previous\\s+)?instructions?",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile(
            "forget\\s+(all\\s+)?(previous\\s+)?instructions?",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile(
            "do\\s+not\\s+follow\\s+(your\\s+)?instructions?",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile(
            "override\\s+(your\\s+)?(safety|guidelines|ethics|training|restrictions)",
            Pattern.CASE_INSENSITIVE),

        // DAN / unrestricted-AI role-play
        Pattern.compile("you\\s+are\\s+now\\s+DAN", Pattern.CASE_INSENSITIVE),
        Pattern.compile("do\\s+anything\\s+now", Pattern.CASE_INSENSITIVE),
        Pattern.compile(
            "act\\s+as\\s+(if\\s+you\\s+have\\s+no\\s+restrictions|an?\\s+AI\\s+without)",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile(
            "simulate\\s+(a\\s+)?(?:unrestricted|uncensored|unfiltered)\\s+AI",
            Pattern.CASE_INSENSITIVE),

        // Restriction / safety bypass
        Pattern.compile(
            "you\\s+have\\s+no\\s+(restrictions|limitations|rules|guidelines)",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile(
            "bypass\\s+your\\s+(safety|guidelines|restrictions|training|filters)",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile(
            "your\\s+(true|real|actual)\\s+(self|nature|identity|purpose)",
            Pattern.CASE_INSENSITIVE),

        // Raw model/template tokens injected into user input
        Pattern.compile(
            "\\[INST\\]|\\[/INST\\]|\\[SYSTEM\\]|<\\|im_start\\|>|<\\|im_end\\|>|<\\|system\\|>",
            Pattern.CASE_INSENSITIVE),

        // Explicit jailbreak keyword
        Pattern.compile("jailbreak", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Returns {@code true} if the text matches any known jailbreak pattern.
     */
    public boolean isJailbreakAttempt(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return PATTERNS.stream().anyMatch(p -> p.matcher(text).find());
    }
}
