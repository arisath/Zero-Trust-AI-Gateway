package com.securellm.filter;

import com.securellm.service.BlocklistService;
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
 * Inspects the inbound request body for:
 *  1. Blocklisted words/phrases ({@link BlocklistService})
 *  2. Prompt injection / jailbreak patterns ({@link JailbreakDetectionService})
 *
 * Either check triggering returns 403 Forbidden immediately.
 * Blocklist is checked first as it is the cheaper operation.
 * Errors during body-reading are logged and treated as pass-through (fail-open).
 */
@Component
public class JailbreakClassifierFilter extends AbstractGatewayFilterFactory<JailbreakClassifierFilter.Config> {

    private static final Logger log = LoggerFactory.getLogger(JailbreakClassifierFilter.class);
    private static final byte[] BLOCKED_BODY =
        "{\"error\":\"Request blocked by security policy.\"}".getBytes(StandardCharsets.UTF_8);

    private final JailbreakDetectionService detector;
    private final BlocklistService blocklistService;

    public JailbreakClassifierFilter(JailbreakDetectionService detector, BlocklistService blocklistService) {
        super(Config.class);
        this.detector = detector;
        this.blocklistService = blocklistService;
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
                    String remote = remoteAddr(exchange);

                    if (blocklistService.isBlocked(body)) {
                        log.warn("Blocklist match '{}' — remote={} path={}",
                            blocklistService.matchedTerm(body), remote, exchange.getRequest().getPath());
                        return reject(exchange);
                    }

                    if (detector.isJailbreakAttempt(body)) {
                        log.warn("Jailbreak attempt detected — remote={} path={}",
                            remote, exchange.getRequest().getPath());
                        return reject(exchange);
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

    private Mono<Void> reject(org.springframework.web.server.ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer errorBuffer = exchange.getResponse().bufferFactory().wrap(BLOCKED_BODY);
        return exchange.getResponse().writeWith(Mono.just(errorBuffer));
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
