package com.securellm.filter;

import com.securellm.service.TokenUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenGuardFilterTest {

    @Mock
    TokenUsageService tokenUsageService;

    TokenGuardFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TokenGuardFilter(tokenUsageService);
    }

    private GatewayFilterChain chainThatRecords(AtomicBoolean called) {
        return exchange -> { called.set(true); return Mono.empty(); };
    }

    @Nested
    class WithinLimits {
        @Test
        void withinBothLimits_callsChain() {
            when(tokenUsageService.isWithinRequestLimit(anyString(), anyString())).thenReturn(Mono.just(true));
            when(tokenUsageService.isWithinTokenLimit(anyString(), anyLong(), anyString())).thenReturn(Mono.just(true));
            when(tokenUsageService.recordRequest(anyString())).thenReturn(Mono.empty());
            when(tokenUsageService.recordTokens(anyString(), anyLong())).thenReturn(Mono.empty());

            MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/llm/chat").build());

            AtomicBoolean chainCalled = new AtomicBoolean(false);

            StepVerifier.create(
                filter.apply(new TokenGuardFilter.Config())
                    .filter(exchange, chainThatRecords(chainCalled)))
                .verifyComplete();

            assertThat(chainCalled.get()).isTrue();
            assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    @Nested
    class RateLimitExceeded {
        @Test
        void requestLimitExceeded_returns429WithRetryAfter() {
            when(tokenUsageService.isWithinRequestLimit(anyString(), anyString())).thenReturn(Mono.just(false));

            MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/llm/chat").build());

            AtomicBoolean chainCalled = new AtomicBoolean(false);

            StepVerifier.create(
                filter.apply(new TokenGuardFilter.Config())
                    .filter(exchange, chainThatRecords(chainCalled)))
                .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("60");
            assertThat(chainCalled.get()).isFalse();
        }

        @Test
        void tokenLimitExceeded_returns429WithRetryAfter() {
            when(tokenUsageService.isWithinRequestLimit(anyString(), anyString())).thenReturn(Mono.just(true));
            when(tokenUsageService.isWithinTokenLimit(anyString(), anyLong(), anyString())).thenReturn(Mono.just(false));

            MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/llm/chat").build());

            AtomicBoolean chainCalled = new AtomicBoolean(false);

            StepVerifier.create(
                filter.apply(new TokenGuardFilter.Config())
                    .filter(exchange, chainThatRecords(chainCalled)))
                .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("3600");
            assertThat(chainCalled.get()).isFalse();
        }
    }

    @Nested
    class UserIdResolution {
        @Test
        void xUserIdHeader_usedAsIdentity() {
            when(tokenUsageService.isWithinRequestLimit(eq("custom-user"), anyString())).thenReturn(Mono.just(true));
            when(tokenUsageService.isWithinTokenLimit(anyString(), anyLong(), anyString())).thenReturn(Mono.just(true));
            when(tokenUsageService.recordRequest(anyString())).thenReturn(Mono.empty());
            when(tokenUsageService.recordTokens(anyString(), anyLong())).thenReturn(Mono.empty());

            MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/llm/chat")
                    .header("X-User-Id", "custom-user")
                    .build());

            StepVerifier.create(
                filter.apply(new TokenGuardFilter.Config())
                    .filter(exchange, ex -> Mono.empty()))
                .verifyComplete();

            verify(tokenUsageService).isWithinRequestLimit(eq("custom-user"), anyString());
        }
    }
}
