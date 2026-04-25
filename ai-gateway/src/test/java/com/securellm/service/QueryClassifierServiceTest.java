package com.securellm.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryClassifierServiceTest {

    @Mock
    LlmService llmService;

    QueryClassifierService service;

    @BeforeEach
    void setUp() {
        service = new QueryClassifierService(llmService, "gemma3:1b");
    }

    @Nested
    class CategoryParsing {

        @ParameterizedTest(name = "model responds ''{0}'' → {1}")
        @CsvSource({
            "PROGRAMMING,       PROGRAMMING",
            "MATHEMATICS,       MATHEMATICS",
            "HISTORY,           HISTORY",
            "SCIENCE,           SCIENCE",
            "CREATIVE_WRITING,  CREATIVE_WRITING",
            "LEGAL,             LEGAL",
            "MEDICAL,           MEDICAL",
            "GENERAL,           GENERAL"
        })
        void parsesExactCategoryName(String modelResponse, QueryCategory expected) {
            when(llmService.processPrompt(anyString(), eq("gemma3:1b")))
                .thenReturn(Mono.just(modelResponse.strip()));

            StepVerifier.create(service.classify("any prompt"))
                .expectNext(expected)
                .verifyComplete();
        }

        @Test
        void parsesLowercaseResponse() {
            when(llmService.processPrompt(anyString(), anyString()))
                .thenReturn(Mono.just("programming"));

            StepVerifier.create(service.classify("write a function"))
                .expectNext(QueryCategory.PROGRAMMING)
                .verifyComplete();
        }

        @Test
        void parsesMixedCaseResponse() {
            when(llmService.processPrompt(anyString(), anyString()))
                .thenReturn(Mono.just("Mathematics"));

            StepVerifier.create(service.classify("solve x^2 + 2 = 0"))
                .expectNext(QueryCategory.MATHEMATICS)
                .verifyComplete();
        }

        @Test
        void parsesResponseWithSurroundingWhitespace() {
            when(llmService.processPrompt(anyString(), anyString()))
                .thenReturn(Mono.just("  HISTORY  "));

            StepVerifier.create(service.classify("tell me about the Roman Empire"))
                .expectNext(QueryCategory.HISTORY)
                .verifyComplete();
        }

        @Test
        void parsesResponseWithExtraText() {
            when(llmService.processPrompt(anyString(), anyString()))
                .thenReturn(Mono.just("The category is: SCIENCE"));

            StepVerifier.create(service.classify("how does photosynthesis work"))
                .expectNext(QueryCategory.SCIENCE)
                .verifyComplete();
        }
    }

    @Nested
    class FallbackBehaviour {

        @Test
        void unknownCategoryDefaultsToGeneral() {
            when(llmService.processPrompt(anyString(), anyString()))
                .thenReturn(Mono.just("SPORTS"));

            StepVerifier.create(service.classify("who won the World Cup"))
                .expectNext(QueryCategory.GENERAL)
                .verifyComplete();
        }

        @Test
        void emptyResponseDefaultsToGeneral() {
            when(llmService.processPrompt(anyString(), anyString()))
                .thenReturn(Mono.just(""));

            StepVerifier.create(service.classify("something"))
                .expectNext(QueryCategory.GENERAL)
                .verifyComplete();
        }

        @Test
        void llmErrorDefaultsToGeneral() {
            when(llmService.processPrompt(anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("classifier timeout")));

            StepVerifier.create(service.classify("any prompt"))
                .expectNext(QueryCategory.GENERAL)
                .verifyComplete();
        }
    }

    @Nested
    class ClassifierModelUsed {

        @Test
        void usesConfiguredClassifierModel() {
            QueryClassifierService customService = new QueryClassifierService(llmService, "tinyllama:1b");
            when(llmService.processPrompt(anyString(), eq("tinyllama:1b")))
                .thenReturn(Mono.just("GENERAL"));

            StepVerifier.create(customService.classify("any prompt"))
                .expectNext(QueryCategory.GENERAL)
                .verifyComplete();
        }
    }
}
