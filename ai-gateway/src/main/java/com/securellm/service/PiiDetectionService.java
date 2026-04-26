package com.securellm.service;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex-based PII detection with reversible tokenization.
 *
 * Tokenization replaces PII in the prompt with stable placeholder tokens
 * (e.g. __PII_EMAIL_1__) before the text is sent to the LLM. The returned
 * token map is used to restore original values in the LLM response.
 *
 * Covers: email addresses, US phone numbers, SSNs, credit card numbers,
 * IPv4 addresses, and US ZIP+4 codes. Extend patterns as needed.
 */
@Service
public class PiiDetectionService {

    private record PiiPattern(Pattern pattern, String tokenType) {}

    private static final List<PiiPattern> PATTERNS = List.of(
        new PiiPattern(
            Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}"),
            "EMAIL"
        ),
        new PiiPattern(
            Pattern.compile("\\b(?!000|666|9\\d{2})\\d{3}[- ]?(?!00)\\d{2}[- ]?(?!0000)\\d{4}\\b"),
            "SSN"
        ),
        new PiiPattern(
            Pattern.compile("\\b(?:\\d[ \\-]?){13,16}\\b"),
            "CARD"
        ),
        // Requires separators — bare 10-digit numbers (e.g. math results) are not matched
        new PiiPattern(
            Pattern.compile("(?:\\+1[\\s\\-]?)?(?:\\(?\\d{3}\\)?[\\s.\\-]+)\\d{3}[\\s.\\-]+\\d{4}\\b"),
            "PHONE"
        ),
        new PiiPattern(
            Pattern.compile("\\b(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\b"),
            "IP"
        ),
        // ZIP+4 only — plain 5-digit numbers are too ambiguous in numeric output
        new PiiPattern(
            Pattern.compile("\\b\\d{5}-\\d{4}\\b"),
            "ZIP"
        )
    );

    public record TokenizedResult(String text, Map<String, String> tokenMap) {}

    /**
     * Replaces PII in {@code input} with stable tokens like {@code __PII_EMAIL_1__}.
     * Returns the tokenized string and the map needed to restore original values.
     */
    public TokenizedResult tokenize(String input) {
        if (input == null || input.isBlank()) {
            return new TokenizedResult(input, Map.of());
        }
        Map<String, String> tokenMap = new LinkedHashMap<>();
        Map<String, Integer> counters = new HashMap<>();
        String result = input;
        for (PiiPattern p : PATTERNS) {
            Matcher m = p.pattern().matcher(result);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String matched = m.group();
                int idx = counters.merge(p.tokenType(), 1, Integer::sum);
                String token = "__PII_" + p.tokenType() + "_" + idx + "__";
                tokenMap.put(token, matched);
                m.appendReplacement(sb, Matcher.quoteReplacement(token));
            }
            m.appendTail(sb);
            result = sb.toString();
        }
        return new TokenizedResult(result, Collections.unmodifiableMap(tokenMap));
    }

    /**
     * Restores original PII values in {@code input} using the map produced by {@link #tokenize}.
     */
    public String detokenize(String input, Map<String, String> tokenMap) {
        if (input == null || tokenMap.isEmpty()) {
            return input;
        }
        String result = input;
        for (Map.Entry<String, String> entry : tokenMap.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
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
