package com.securellm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Production security configuration (active on all profiles except "local").
 *
 * Validates Bearer JWTs using the JWKS endpoint of your identity provider.
 * Set JWT_JWKS_URI in the environment (or application.yaml) to point at your CIAM's
 * well-known JWKS URL, e.g.:
 *   https://auth.example.com/.well-known/jwks.json
 *
 * CSRF is disabled because the gateway is a stateless REST API (no browser sessions).
 */
@Configuration
@EnableWebFluxSecurity
@Profile("!local")
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private String jwksUri;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                // Allow health / metrics endpoints without a token
                .pathMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtDecoder(jwtDecoder()))
            );
        return http.build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri).build();
    }
}
