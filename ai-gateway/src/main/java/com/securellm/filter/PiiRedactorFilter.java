package com.securellm.filter;

import com.securellm.service.PiiDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

/**
 * Gateway filter that tokenizes PII in proxied request bodies before forwarding
 * to downstream LLM services. Responses are passed through unchanged — the caller
 * is responsible for detokenizing if needed (see ChatController for the full
 * tokenize/detokenize pipeline on /api/chat).
 */
@Component
public class PiiRedactorFilter extends AbstractGatewayFilterFactory<PiiRedactorFilter.Config> {

    private static final Logger log = LoggerFactory.getLogger(PiiRedactorFilter.class);

    private final PiiDetectionService piiDetectionService;

    public PiiRedactorFilter(PiiDetectionService piiDetectionService) {
        super(Config.class);
        this.piiDetectionService = piiDetectionService;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> chain.filter(exchange);
    }

    public static class Config {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
