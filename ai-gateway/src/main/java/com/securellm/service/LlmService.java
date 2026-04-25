package com.securellm.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * WebClient wrapper around the Ollama chat API.
 *
 * Configure via:
 *   llm.ollama.base-url       — defaults to http://localhost:11434
 *   llm.ollama.model          — defaults to llama3.2
 *   llm.ollama.timeout-seconds — defaults to 120 (local models can be slow)
 *
 * Ollama chat endpoint: POST /api/chat
 * Request:  { "model": "...", "messages": [...], "stream": false }
 * Response: { "message": { "role": "assistant", "content": "..." }, "done": true }
 */
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final WebClient webClient;
    private final String model;
    private final Duration timeout;

    public LlmService(
            WebClient.Builder webClientBuilder,
            @Value("${llm.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${llm.ollama.model:llama3.2}") String model,
            @Value("${llm.ollama.timeout-seconds:120}") long timeoutSeconds) {

        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.webClient = webClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    public Mono<String> processPrompt(String prompt) {
        return processPrompt(prompt, model);
    }

    public Mono<String> processPrompt(String prompt, String modelOverride) {
        Map<String, Object> requestBody = Map.of(
            "model", modelOverride,
            "messages", List.of(Map.of("role", "user", "content", prompt)),
            "stream", false
        );

        return webClient.post()
            .uri("/api/chat")
            .bodyValue(requestBody)
            .retrieve()
            .onStatus(status -> !status.is2xxSuccessful(), clientResponse ->
                clientResponse.bodyToMono(JsonNode.class)
                    .map(body -> {
                        String ollamaError = body.path("error").asText("unknown error");
                        log.error("Ollama error {}: {}", clientResponse.statusCode(), ollamaError);
                        return new IllegalStateException("Ollama: " + ollamaError);
                    })
                    .cast(Throwable.class)
            )
            .bodyToMono(JsonNode.class)
            .timeout(timeout)
            .map(json -> json.path("message").path("content").asText(""));
    }
}
