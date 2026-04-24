package com.securellm.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Regex-based PII detection and redaction service.
 *
 * Covers: email addresses, US phone numbers, SSNs, credit card numbers,
 * IPv4 addresses, and US postal codes. Extend patterns as needed.
 */
@Service
public class PiiDetectionService {

    private record PiiPattern(Pattern pattern, String placeholder) {}

    private static final List<PiiPattern> PATTERNS = List.of(
        // Email addresses
        new PiiPattern(
            Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}"),
            "[REDACTED-EMAIL]"
        ),
        // US Social Security Numbers (###-##-#### or #########)
        new PiiPattern(
            Pattern.compile("\\b(?!000|666|9\\d{2})\\d{3}[- ]?(?!00)\\d{2}[- ]?(?!0000)\\d{4}\\b"),
            "[REDACTED-SSN]"
        ),
        // Credit/debit card numbers (13–16 digits, optional separators)
        new PiiPattern(
            Pattern.compile("\\b(?:\\d[ \\-]?){13,16}\\b"),
            "[REDACTED-CARD]"
        ),
        // US phone numbers — (###) ###-####, ###-###-####, ##########, +1##########
        new PiiPattern(
            Pattern.compile("(?:\\+1[\\s\\-]?)?(?:\\(?\\d{3}\\)?[\\s.\\-]?)\\d{3}[\\s.\\-]?\\d{4}\\b"),
            "[REDACTED-PHONE]"
        ),
        // IPv4 addresses
        new PiiPattern(
            Pattern.compile("\\b(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\b"),
            "[REDACTED-IP]"
        ),
        // US ZIP codes (5-digit or ZIP+4)
        new PiiPattern(
            Pattern.compile("\\b\\d{5}(?:-\\d{4})?\\b"),
            "[REDACTED-ZIP]"
        )
    );

    /**
     * Scans {@code input} for PII and replaces each match with a labelled placeholder.
     * Returns the original string unchanged if no PII is detected.
     */
    public String redactPii(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        String result = input;
        for (PiiPattern p : PATTERNS) {
            result = p.pattern().matcher(result).replaceAll(p.placeholder());
        }
        return result;
    }

    /**
     * Returns {@code true} if the input contains at least one PII match.
     */
    public boolean containsPii(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        return PATTERNS.stream().anyMatch(p -> p.pattern().matcher(input).find());
    }
}
