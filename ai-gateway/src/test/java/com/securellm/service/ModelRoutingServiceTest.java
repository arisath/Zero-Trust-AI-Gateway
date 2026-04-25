package com.securellm.service;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRoutingServiceTest {

    private ModelRoutingService serviceWithDefaults() {
        return new ModelRoutingService(
            "codellama:7b",
            "qwen2.5-math:7b",
            "llama3.2:3b",
            "llama3.2:3b",
            "mistral:7b",
            "llama3.2:3b",
            "llama3.2:3b",
            "gemma3:1b"
        );
    }

    @Nested
    class DefaultModelMapping {

        @Test
        void programmingMapsToCodellama() {
            assertThat(serviceWithDefaults().modelFor(QueryCategory.PROGRAMMING))
                .isEqualTo("codellama:7b");
        }

        @Test
        void mathematicsMapsToQwen() {
            assertThat(serviceWithDefaults().modelFor(QueryCategory.MATHEMATICS))
                .isEqualTo("qwen2.5-math:7b");
        }

        @Test
        void creativeWritingMapsToMistral() {
            assertThat(serviceWithDefaults().modelFor(QueryCategory.CREATIVE_WRITING))
                .isEqualTo("mistral:7b");
        }

        @Test
        void generalMapsToGemma() {
            assertThat(serviceWithDefaults().modelFor(QueryCategory.GENERAL))
                .isEqualTo("gemma3:1b");
        }

        @ParameterizedTest
        @EnumSource(value = QueryCategory.class, names = {"HISTORY", "SCIENCE", "LEGAL", "MEDICAL"})
        void generalKnowledgeCategoriesMapToLlama(QueryCategory category) {
            assertThat(serviceWithDefaults().modelFor(category))
                .isEqualTo("llama3.2:3b");
        }
    }

    @Nested
    class CustomModelMapping {

        @Test
        void customProgrammingModelIsReturned() {
            ModelRoutingService service = new ModelRoutingService(
                "deepseek-coder:6.7b",
                "qwen2.5-math:7b",
                "llama3.2:3b",
                "llama3.2:3b",
                "mistral:7b",
                "llama3.2:3b",
                "llama3.2:3b",
                "gemma3:1b"
            );
            assertThat(service.modelFor(QueryCategory.PROGRAMMING))
                .isEqualTo("deepseek-coder:6.7b");
        }

        @Test
        void everyCategoryHasAMapping() {
            ModelRoutingService service = serviceWithDefaults();
            for (QueryCategory category : QueryCategory.values()) {
                assertThat(service.modelFor(category))
                    .as("category %s should have a non-blank model", category)
                    .isNotBlank();
            }
        }
    }
}
