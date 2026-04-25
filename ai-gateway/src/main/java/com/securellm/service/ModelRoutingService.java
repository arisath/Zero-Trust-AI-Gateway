package com.securellm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

@Service
public class ModelRoutingService {

    private static final Logger log = LoggerFactory.getLogger(ModelRoutingService.class);

    private final Map<QueryCategory, String> modelMap;

    public ModelRoutingService(
            @Value("${llm.ollama.models.programming:codellama:7b}") String programmingModel,
            @Value("${llm.ollama.models.mathematics:qwen2.5-math:7b}") String mathematicsModel,
            @Value("${llm.ollama.models.history:llama3.2:3b}") String historyModel,
            @Value("${llm.ollama.models.science:llama3.2:3b}") String scienceModel,
            @Value("${llm.ollama.models.creative-writing:mistral:7b}") String creativeWritingModel,
            @Value("${llm.ollama.models.legal:llama3.2:3b}") String legalModel,
            @Value("${llm.ollama.models.medical:llama3.2:3b}") String medicalModel,
            @Value("${llm.ollama.models.general:gemma3:1b}") String generalModel) {

        modelMap = new EnumMap<>(QueryCategory.class);
        modelMap.put(QueryCategory.PROGRAMMING, programmingModel);
        modelMap.put(QueryCategory.MATHEMATICS, mathematicsModel);
        modelMap.put(QueryCategory.HISTORY, historyModel);
        modelMap.put(QueryCategory.SCIENCE, scienceModel);
        modelMap.put(QueryCategory.CREATIVE_WRITING, creativeWritingModel);
        modelMap.put(QueryCategory.LEGAL, legalModel);
        modelMap.put(QueryCategory.MEDICAL, medicalModel);
        modelMap.put(QueryCategory.GENERAL, generalModel);
    }

    public String modelFor(QueryCategory category) {
        String model = modelMap.getOrDefault(category, modelMap.get(QueryCategory.GENERAL));
        log.info("Routing category {} → model '{}'", category, model);
        return model;
    }
}
