package com.securellm.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Reactive, Redis-backed sliding-window rate limiter with per-tier limits.
 *
 * Two independent limits are enforced per user identity:
 *   1. Requests per minute  — guarded by a 2-minute key TTL.
 *   2. Estimated tokens per hour — guarded by a 2-hour key TTL.
 *
 * Limits differ by tier: free users get lower caps, premium users get higher caps.
 * Tier is derived from the JWT claim set by the auth service at login.
 *
 * Redis errors are caught and treated as "within limit" so that a Redis outage
 * does not hard-block all traffic.
 */
@Service
public class TokenUsageService {

    private final ReactiveRedisTemplate<String, String> redis;

    @Value("${gateway.token-guard.free.max-requests-per-minute:10}")
    private long freeMaxRequestsPerMinute;

    @Value("${gateway.token-guard.premium.max-requests-per-minute:60}")
    private long premiumMaxRequestsPerMinute;

    @Value("${gateway.token-guard.free.max-tokens-per-hour:10000}")
    private long freeMaxTokensPerHour;

    @Value("${gateway.token-guard.premium.max-tokens-per-hour:100000}")
    private long premiumMaxTokensPerHour;

    public TokenUsageService(@Qualifier("reactiveStringRedisTemplate") ReactiveRedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    // -------------------------------------------------------------------------
    // Limit checks
    // -------------------------------------------------------------------------

    public Mono<Boolean> isWithinRequestLimit(String userId, String tier) {
        long limit = "premium".equals(tier) ? premiumMaxRequestsPerMinute : freeMaxRequestsPerMinute;
        String key = requestKey(userId);
        return redis.opsForValue().get(key)
            .defaultIfEmpty("0")
            .map(count -> Long.parseLong(count) < limit)
            .onErrorReturn(true);
    }

    public Mono<Boolean> isWithinTokenLimit(String userId, long estimatedTokens, String tier) {
        long limit = "premium".equals(tier) ? premiumMaxTokensPerHour : freeMaxTokensPerHour;
        String key = tokenKey(userId);
        return redis.opsForValue().get(key)
            .defaultIfEmpty("0")
            .map(count -> Long.parseLong(count) + estimatedTokens <= limit)
            .onErrorReturn(true);
    }

    // -------------------------------------------------------------------------
    // Usage recording
    // -------------------------------------------------------------------------

    public Mono<Void> recordRequest(String userId) {
        String key = requestKey(userId);
        return redis.opsForValue().increment(key)
            .flatMap(newCount -> newCount == 1
                ? redis.expire(key, Duration.ofMinutes(2)).then()
                : Mono.empty())
            .onErrorComplete();
    }

    public Mono<Void> recordTokens(String userId, long tokenCount) {
        String key = tokenKey(userId);
        return redis.opsForValue().increment(key, tokenCount)
            .flatMap(newCount -> newCount == tokenCount
                ? redis.expire(key, Duration.ofHours(2)).then()
                : Mono.empty())
            .onErrorComplete();
    }

    // -------------------------------------------------------------------------
    // Key helpers
    // -------------------------------------------------------------------------

    private String requestKey(String userId) {
        return "gateway:rate:" + sanitize(userId) + ":req:" + currentMinuteBucket();
    }

    private String tokenKey(String userId) {
        return "gateway:rate:" + sanitize(userId) + ":tok:" + currentHourBucket();
    }

    private String currentMinuteBucket() {
        return String.valueOf(System.currentTimeMillis() / 60_000);
    }

    private String currentHourBucket() {
        return String.valueOf(System.currentTimeMillis() / 3_600_000);
    }

    private String sanitize(String userId) {
        return userId.replaceAll("[^a-zA-Z0-9._@-]", "_");
    }
}
