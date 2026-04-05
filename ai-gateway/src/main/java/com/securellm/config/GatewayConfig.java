package com.securellm.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("llm-service", r -> r.path("/api/llm/**")
                        .uri("lb://llm-service"))
                .route("secure-llm", r -> r.path("/api/secure-llm/**")
                        .uri("lb://secure-llm"))
                .build();
    }
}
