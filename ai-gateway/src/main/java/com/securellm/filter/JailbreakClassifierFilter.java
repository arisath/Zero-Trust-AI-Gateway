package com.securellm.filter;

import com.securellm.service.JailbreakDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Inspects the inbound request body for prompt-injection / jailbreak patterns.
 *
 * Flow:
 *  1. Buffer the entire request body reactively.
 *  2. Run it through {@link JailbreakDetectionService}.
 *  3. If flagged → return 403 Forbidden immediately.
 *  4. If clean  → re-wrap the body (so downstream filters can read it) and continue.
 *
 * Requests with no body (GET, etc.) are passed through unchanged.
 * Errors during body-reading are logged and treated as pass-through (fail-open).
 */
@Component
public class JailbreakClassifierFilter extends AbstractGatewayFilterFactory<JailbreakClassifierFilter.Config> {

    private static final Logger log = LoggerFactory.getLogger(JailbreakClassifierFilter.class);
    private static final byte[] BLOCKED_BODY =
        "{\"error\":\"Request blocked by security policy.\"}".getBytes(StandardCharsets.UTF_8);

    private final JailbreakDetectionService detector;

    public JailbreakClassifierFilter(JailbreakDetectionService detector) {
        super(Config.class);
        this.detector = detector;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            if (!config.isEnabled()) {
                return chain.filter(exchange);
            }

            return DataBufferUtils.join(exchange.getRequest().getBody())
                .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    String body = new String(bytes, StandardCharsets.UTF_8);

                    if (detector.isJailbreakAttempt(body)) {
                        String remote = remoteAddr(exchange);
                        log.warn("Jailbreak attempt detected — remote={} path={}",
                            remote, exchange.getRequest().getPath());
                        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        DataBuffer errorBuffer =
                            exchange.getResponse().bufferFactory().wrap(BLOCKED_BODY);
                        return exchange.getResponse().writeWith(Mono.just(errorBuffer));
                    }

                    // Re-wrap the consumed body so the next filter / downstream can read it
                    DataBuffer rewrapped = exchange.getResponse().bufferFactory().wrap(bytes);
                    ServerHttpRequest mutated = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return Flux.just(rewrapped);
                        }
                    };
                    return chain.filter(exchange.mutate().request(mutated).build());
                })
                .onErrorResume(ex -> {
                    log.error("Error reading request body in JailbreakClassifierFilter", ex);
                    return chain.filter(exchange); // fail-open
                });
        };
    }

    private String remoteAddr(org.springframework.web.server.ServerWebExchange exchange) {
        InetSocketAddress addr = exchange.getRequest().getRemoteAddress();
        return addr != null ? addr.toString() : "unknown";
    }

    public static class Config {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
