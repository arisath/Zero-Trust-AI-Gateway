package com.securellm.service;

import org.springframework.stereotype.Service;

@Service
public class LlmService {

    public String processPrompt(String prompt) {
        // Implementation would use LangChain4j to orchestrate LLM calls
        // Supports OpenAI, Gemini, Ollama, etc.
        return "Response from LLM";
    }
}
