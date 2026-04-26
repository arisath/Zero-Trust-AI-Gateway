package com.securellm.filter;

import com.securellm.service.TokenUsageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Enforces per-user, per-tier rate limits before a request is forwarded to the LLM service.
 *
 * User identity is resolved in order:
 *   1. JWT principal name (populated by Spring Security in production profile).
 *   2. X-User-Id request header (useful in local / service-to-service calls).
 *   3. Falls back to "anonymous".
 *
 * Tier is extracted from the "tier" JWT claim (set by the auth service at login).
 * Defaults to "free" when no JWT is present (local profile or service-to-service).
 *
 * Two limits are checked per tier:
 *   - Requests per minute  → HTTP 429 with Retry-After: 60
 *   - Estimated tokens per hour → HTTP 429 with Retry-After: 3600
 */
@Component
public class TokenGuardFilter extends AbstractGatewayFilterFactory<TokenGuardFilter.Config> {

    private static final Logger log = LoggerFactory.getLogger(TokenGuardFilter.class);
    private static final String ANONYMOUS = "anonymous";
    private static final byte[] RATE_LIMIT_BODY =
        "{\"error\":\"Rate limit exceeded. Please slow down.\"}".getBytes(StandardCharsets.UTF_8);

    private final TokenUsageService tokenUsageService;

    public TokenGuardFilter(TokenUsageService tokenUsageService) {
        super(Config.class);
        this.tokenUsageService = tokenUsageService;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            Mono<String> userIdMono = exchange.getPrincipal()
                .map(p -> p.getName())
                .defaultIfEmpty(ANONYMOUS)
                .map(name -> {
                    String header = exchange.getRequest().getHeaders().getFirst("X-User-Id");
                    return (header != null && !header.isBlank()) ? header : name;
                });

            Mono<String> tierMono = exchange.getPrincipal()
                .filter(p -> p instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(jwt -> {
                    String tier = jwt.getToken().getClaimAsString("tier");
                    return tier != null ? tier : "free";
                })
                .defaultIfEmpty("free");

            long contentLength = exchange.getRequest().getHeaders().getContentLength();
            long estimatedTokens = Math.min(contentLength > 0 ? contentLength / 4 : 256, 4096);

            return Mono.zip(userIdMono, tierMono)
                .flatMap(tuple -> {
                    String userId = tuple.getT1();
                    String tier = tuple.getT2();
                    return tokenUsageService.isWithinRequestLimit(userId, tier)
                        .flatMap(withinRequestLimit -> {
                            if (!withinRequestLimit) {
                                log.warn("Request rate limit exceeded for user={} tier={}", userId, tier);
                                return rejectWithTooManyRequests(exchange, "60");
                            }
                            return tokenUsageService.isWithinTokenLimit(userId, estimatedTokens, tier)
                                .flatMap(withinTokenLimit -> {
                                    if (!withinTokenLimit) {
                                        log.warn("Token rate limit exceeded for user={} tier={}", userId, tier);
                                        return rejectWithTooManyRequests(exchange, "3600");
                                    }
                                    return chain.filter(exchange)
                                        .then(tokenUsageService.recordRequest(userId))
                                        .then(tokenUsageService.recordTokens(userId, estimatedTokens));
                                });
                        });
                });
        };
    }

    private Mono<Void> rejectWithTooManyRequests(
            org.springframework.web.server.ServerWebExchange exchange, String retryAfter) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("Retry-After", retryAfter);
        var buffer = exchange.getResponse().bufferFactory().wrap(RATE_LIMIT_BODY);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    public static class Config {}
}
