package com.securellm.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenUsageServiceTest {

    @Mock
    ReactiveRedisTemplate<String, String> redis;

    @Mock
    ReactiveValueOperations<String, String> valueOps;

    TokenUsageService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        service = new TokenUsageService(redis);
        ReflectionTestUtils.setField(service, "maxRequestsPerMinute", 60L);
        ReflectionTestUtils.setField(service, "maxTokensPerHour", 100_000L);
    }

    @Nested
    class RequestLimit {
        @Test
        void withinLimit_whenCountBelowMax() {
            when(valueOps.get(anyString())).thenReturn(Mono.just("59"));

            StepVerifier.create(service.isWithinRequestLimit("user1"))
                .expectNext(true)
                .verifyComplete();
        }

        @Test
        void exceededLimit_whenCountAtMax() {
            when(valueOps.get(anyString())).thenReturn(Mono.just("60"));

            StepVerifier.create(service.isWithinRequestLimit("user1"))
                .expectNext(false)
                .verifyComplete();
        }

        @Test
        void withinLimit_whenKeyMissingInRedis() {
            when(valueOps.get(anyString())).thenReturn(Mono.empty());

            StepVerifier.create(service.isWithinRequestLimit("user1"))
                .expectNext(true)
                .verifyComplete();
        }

        @Test
        void failsOpen_whenRedisErrors() {
            when(valueOps.get(anyString())).thenReturn(Mono.error(new RuntimeException("Redis down")));

            StepVerifier.create(service.isWithinRequestLimit("user1"))
                .expectNext(true)
                .verifyComplete();
        }
    }

    @Nested
    class TokenLimit {
        @Test
        void withinLimit_whenSumBelowMax() {
            when(valueOps.get(anyString())).thenReturn(Mono.just("50000"));

            StepVerifier.create(service.isWithinTokenLimit("user1", 49999))
                .expectNext(true)
                .verifyComplete();
        }

        @Test
        void exceededLimit_whenSumExceedsMax() {
            when(valueOps.get(anyString())).thenReturn(Mono.just("99000"));

            StepVerifier.create(service.isWithinTokenLimit("user1", 1001))
                .expectNext(false)
                .verifyComplete();
        }

        @Test
        void failsOpen_whenRedisErrors() {
            when(valueOps.get(anyString())).thenReturn(Mono.error(new RuntimeException("Redis down")));

            StepVerifier.create(service.isWithinTokenLimit("user1", 500))
                .expectNext(true)
                .verifyComplete();
        }
    }

    @Nested
    class RecordRequest {
        @Test
        void setsTtlOnFirstIncrement() {
            when(valueOps.increment(anyString())).thenReturn(Mono.just(1L));
            when(redis.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

            StepVerifier.create(service.recordRequest("user1"))
                .verifyComplete();

            verify(redis).expire(anyString(), any(Duration.class));
        }

        @Test
        void doesNotSetTtlOnSubsequentIncrements() {
            when(valueOps.increment(anyString())).thenReturn(Mono.just(5L));

            StepVerifier.create(service.recordRequest("user1"))
                .verifyComplete();

            verify(redis, never()).expire(anyString(), any(Duration.class));
        }

        @Test
        void completesWithoutErrorOnRedisFailure() {
            when(valueOps.increment(anyString())).thenReturn(Mono.error(new RuntimeException("Redis down")));

            StepVerifier.create(service.recordRequest("user1"))
                .verifyComplete();
        }
    }

    @Nested
    class RecordTokens {
        @Test
        void setsTtlOnFirstIncrement() {
            when(valueOps.increment(anyString(), anyLong())).thenReturn(Mono.just(256L));
            when(redis.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

            StepVerifier.create(service.recordTokens("user1", 256))
                .verifyComplete();

            verify(redis).expire(anyString(), any(Duration.class));
        }

        @Test
        void completesWithoutErrorOnRedisFailure() {
            when(valueOps.increment(anyString(), anyLong())).thenReturn(Mono.error(new RuntimeException("Redis down")));

            StepVerifier.create(service.recordTokens("user1", 256))
                .verifyComplete();
        }
    }
}
