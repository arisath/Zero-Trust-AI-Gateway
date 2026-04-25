package com.securellm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class QueryClassifierService {

    private static final Logger log = LoggerFactory.getLogger(QueryClassifierService.class);

    private static final String CLASSIFICATION_PROMPT =
        "You are a query classifier. Classify the following query into exactly one of these categories:\n" +
        "PROGRAMMING, MATHEMATICS, HISTORY, SCIENCE, CREATIVE_WRITING, LEGAL, MEDICAL, GENERAL\n\n" +
        "Rules:\n" +
        "- PROGRAMMING: code, software, algorithms, debugging, frameworks, databases, DevOps\n" +
        "- MATHEMATICS: algebra, calculus, statistics, proofs, numerical problems\n" +
        "- HISTORY: historical events, civilizations, wars, biographies of historical figures\n" +
        "- SCIENCE: physics, chemistry, biology, astronomy, earth sciences\n" +
        "- CREATIVE_WRITING: stories, poems, scripts, creative content generation\n" +
        "- LEGAL: laws, regulations, contracts, legal advice, court procedures\n" +
        "- MEDICAL: symptoms, diseases, treatments, medications, health advice\n" +
        "- GENERAL: anything that does not fit the above categories\n\n" +
        "Respond with only the category name in uppercase, nothing else.\n\n" +
        "Query: ";

    private final LlmService llmService;
    private final String classifierModel;

    public QueryClassifierService(
            LlmService llmService,
            @Value("${llm.ollama.classifier-model:gemma3:1b}") String classifierModel) {
        this.llmService = llmService;
        this.classifierModel = classifierModel;
    }

    public Mono<QueryCategory> classify(String prompt) {
        return llmService.processPrompt(CLASSIFICATION_PROMPT + prompt, classifierModel)
            .map(this::parseCategory)
            .doOnNext(cat -> log.info("Query classified as {}", cat))
            .onErrorReturn(QueryCategory.GENERAL);
    }

    private QueryCategory parseCategory(String response) {
        String normalized = response.trim().toUpperCase();
        for (QueryCategory category : QueryCategory.values()) {
            if (normalized.contains(category.name())) {
                return category;
            }
        }
        log.warn("Unrecognised classifier response '{}', defaulting to GENERAL", response);
        return QueryCategory.GENERAL;
    }
}
