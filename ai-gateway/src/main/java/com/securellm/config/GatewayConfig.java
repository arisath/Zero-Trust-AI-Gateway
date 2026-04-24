package com.securellm.config;

import com.securellm.filter.JailbreakClassifierFilter;
import com.securellm.filter.PiiRedactorFilter;
import com.securellm.filter.TokenGuardFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines gateway routes and wires the security filter chain onto each one.
 *
 * Filter order per request (inbound → outbound):
 *   1. TokenGuardFilter        — cheapest check first: reject over-quota requests
 *   2. JailbreakClassifierFilter — inspect request body for prompt injection
 *   3. [request forwarded to downstream LLM service]
 *   4. PiiRedactorFilter        — redact PII from the LLM response (runs on the way back)
 */
@Configuration
public class GatewayConfig {

    private final TokenGuardFilter tokenGuardFilter;
    private final JailbreakClassifierFilter jailbreakClassifierFilter;
    private final PiiRedactorFilter piiRedactorFilter;

    public GatewayConfig(
            TokenGuardFilter tokenGuardFilter,
            JailbreakClassifierFilter jailbreakClassifierFilter,
            PiiRedactorFilter piiRedactorFilter) {
        this.tokenGuardFilter = tokenGuardFilter;
        this.jailbreakClassifierFilter = jailbreakClassifierFilter;
        this.piiRedactorFilter = piiRedactorFilter;
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("llm-service", r -> r
                .path("/api/llm/**")
                .filters(f -> f
                    .filter(tokenGuardFilter.apply(new TokenGuardFilter.Config()))
                    .filter(jailbreakClassifierFilter.apply(new JailbreakClassifierFilter.Config()))
                    .filter(piiRedactorFilter.apply(new PiiRedactorFilter.Config())))
                .uri("lb://llm-service"))
            .route("secure-llm", r -> r
                .path("/api/secure-llm/**")
                .filters(f -> f
                    .filter(tokenGuardFilter.apply(new TokenGuardFilter.Config()))
                    .filter(jailbreakClassifierFilter.apply(new JailbreakClassifierFilter.Config()))
                    .filter(piiRedactorFilter.apply(new PiiRedactorFilter.Config())))
                .uri("lb://secure-llm"))
            .build();
    }
}
