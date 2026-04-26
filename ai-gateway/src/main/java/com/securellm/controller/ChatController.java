package com.securellm.controller;

import com.securellm.service.BlocklistService;
import com.securellm.service.JailbreakDetectionService;
import com.securellm.service.LlmService;
import com.securellm.service.ModelRoutingService;
import com.securellm.service.PiiDetectionService;
import com.securellm.service.QueryClassifierService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Direct chat endpoint that exercises the full security pipeline without
 * routing through a downstream LLM service.
 *
 * POST /api/chat
 * Body: { "prompt": "..." }
 *
 * The controller applies jailbreak detection and PII redaction itself because
 * it is not a gateway route and therefore does not run through the gateway
 * filter chain.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final LlmService llmService;
    private final BlocklistService blocklistService;
    private final JailbreakDetectionService jailbreakDetectionService;
    private final PiiDetectionService piiDetectionService;
    private final QueryClassifierService queryClassifierService;
    private final ModelRoutingService modelRoutingService;

    public ChatController(
            LlmService llmService,
            BlocklistService blocklistService,
            JailbreakDetectionService jailbreakDetectionService,
            PiiDetectionService piiDetectionService,
            QueryClassifierService queryClassifierService,
            ModelRoutingService modelRoutingService) {
        this.llmService = llmService;
        this.blocklistService = blocklistService;
        this.jailbreakDetectionService = jailbreakDetectionService;
        this.piiDetectionService = piiDetectionService;
        this.queryClassifierService = queryClassifierService;
        this.modelRoutingService = modelRoutingService;
    }

    @PostMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request.prompt() == null || request.prompt().isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "prompt is required"));
        }

        if (blocklistService.isBlocked(request.prompt())) {
            log.warn("Blocklist match '{}' blocked in /api/chat", blocklistService.matchedTerm(request.prompt()));
            return Mono.error(new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Request blocked by security policy."));
        }

        if (jailbreakDetectionService.isJailbreakAttempt(request.prompt())) {
            log.warn("Jailbreak attempt blocked in /api/chat");
            return Mono.error(new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Request blocked by security policy."));
        }

        // Tokenize PII in the prompt before sending to the LLM, then restore in the response
        PiiDetectionService.TokenizedResult tokenized = piiDetectionService.tokenize(request.prompt());

        return queryClassifierService.classify(tokenized.text())
            .flatMap(category -> {
                String targetModel = modelRoutingService.modelFor(category);
                return llmService.processPrompt(tokenized.text(), targetModel)
                    .map(response -> piiDetectionService.detokenize(response, tokenized.tokenMap()))
                    .map(response -> new ChatResponse(response, category.name()));
            })
            .onErrorMap(ex -> !(ex instanceof ResponseStatusException), ex -> {
                log.error("LLM call failed", ex);
                return new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "LLM service unavailable");
            });
    }

    public record ChatRequest(String prompt) {}
    public record ChatResponse(String response, String category) {}
}
