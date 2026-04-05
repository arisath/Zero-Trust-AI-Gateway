package com.securellm.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class JailbreakClassifierFilter extends AbstractGatewayFilterFactory<JailbreakClassifierFilter.Config> {

    public JailbreakClassifierFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // Send prompt to local SLM for jailbreak detection
            // Return 403 if prompt is flagged
            return chain.filter(exchange);
        };
    }

    public static class Config {
        // Configuration properties for the filter
    }
}
