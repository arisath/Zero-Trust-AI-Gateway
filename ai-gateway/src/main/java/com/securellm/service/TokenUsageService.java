package com.securellm.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Reactive, Redis-backed sliding-window rate limiter.
 *
 * Two independent limits are enforced per user identity:
 *   1. Requests per minute  — guarded by a 2-minute key TTL.
 *   2. Estimated tokens per hour — guarded by a 2-hour key TTL.
 *
 * Keys use a epoch-bucket scheme (current minute / current hour) so the window
 * resets naturally without an explicit scheduled clean-up job.
 *
 * Redis errors are caught and treated as "within limit" so that a Redis outage
 * does not hard-block all traffic; operators should alert on Redis unavailability
 * separately.
 */
@Service
public class TokenUsageService {

    private final ReactiveRedisTemplate<String, String> redis;

    @Value("${gateway.token-guard.max-requests-per-minute:60}")
    private long maxRequestsPerMinute;

    @Value("${gateway.token-guard.max-tokens-per-hour:100000}")
    private long maxTokensPerHour;

    public TokenUsageService(@Qualifier("reactiveStringRedisTemplate") ReactiveRedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    // -------------------------------------------------------------------------
    // Limit checks
    // -------------------------------------------------------------------------

    /** Returns {@code true} when the user is still below the per-minute request cap. */
    public Mono<Boolean> isWithinRequestLimit(String userId) {
        String key = requestKey(userId);
        return redis.opsForValue().get(key)
            .defaultIfEmpty("0")
            .map(count -> Long.parseLong(count) < maxRequestsPerMinute)
            .onErrorReturn(true); // fail-open on Redis outage
    }

    /** Returns {@code true} when adding {@code estimatedTokens} stays under the hourly cap. */
    public Mono<Boolean> isWithinTokenLimit(String userId, long estimatedTokens) {
        String key = tokenKey(userId);
        return redis.opsForValue().get(key)
            .defaultIfEmpty("0")
            .map(count -> Long.parseLong(count) + estimatedTokens <= maxTokensPerHour)
            .onErrorReturn(true);
    }

    // -------------------------------------------------------------------------
    // Usage recording
    // -------------------------------------------------------------------------

    /** Increments the per-minute request counter for the user. */
    public Mono<Void> recordRequest(String userId) {
        String key = requestKey(userId);
        return redis.opsForValue().increment(key)
            .flatMap(newCount -> newCount == 1
                ? redis.expire(key, Duration.ofMinutes(2)).then()
                : Mono.empty())
            .onErrorComplete();
    }

    /** Increments the per-hour token counter by {@code tokenCount}. */
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

    /** Strips characters that are invalid in Redis key names. */
    private String sanitize(String userId) {
        return userId.replaceAll("[^a-zA-Z0-9._@-]", "_");
    }
}
