package com.securellm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;
import org.springframework.security.oauth2.jwt.Jwt;

@Configuration
@EnableWebFluxSecurity
@Profile("!local") // This only loads when -Dspring.profiles.active=local
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            .authorizeExchange(exchanges -> exchanges
                .anyExchange().authenticated()
            )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                // Change '.decoder(' to '.jwtDecoder('
                                .jwtDecoder(jwtDecoder())
                        )
                );
        return http.build();
    }

    // Placeholder for JWT decoder - would be implemented based on your CIAM
    @Bean
     org.springframework.security.oauth2.jwt.ReactiveJwtDecoder jwtDecoder()
    {
        return (String token) -> {
            return Mono.error(new JwtException("Decoder not yet implemented"));
        };

    }
}
