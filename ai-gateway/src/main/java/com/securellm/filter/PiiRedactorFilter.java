package com.securellm.filter;

import com.securellm.service.PiiDetectionService;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Intercepts the LLM service response and redacts any PII before returning it
 * to the caller.
 *
 * Only text-based content types (application/json, text/*) are inspected.
 * Binary and streaming responses are passed through unchanged.
 *
 * The Content-Length header is removed after redaction because the byte count
 * may change, preventing a length mismatch on the wire.
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
        return (exchange, chain) -> {
            if (!config.isEnabled()) {
                return chain.filter(exchange);
            }

            ServerHttpResponse originalResponse = exchange.getResponse();
            DataBufferFactory bufferFactory = originalResponse.bufferFactory();

            ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
                @Override
                public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                    if (!isTextContent(getHeaders())) {
                        return super.writeWith(body); // skip binary content
                    }

                    // Remove Content-Length — byte count may change after redaction
                    getHeaders().remove(HttpHeaders.CONTENT_LENGTH);

                    Flux<DataBuffer> buffered = Flux.from(body);

                    return super.writeWith(
                        DataBufferUtils.join(buffered).map(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);

                            String responseBody = new String(bytes, StandardCharsets.UTF_8);
                            String redacted = piiDetectionService.redactPii(responseBody);

                            if (!responseBody.equals(redacted)) {
                                log.info("PII detected and redacted in response from {}",
                                    exchange.getRequest().getPath());
                            }

                            return bufferFactory.wrap(redacted.getBytes(StandardCharsets.UTF_8));
                        })
                        .onErrorResume(ex -> {
                            log.error("Error during PII redaction", ex);
                            // Return original bytes unchanged on error
                            return Mono.empty();
                        })
                    );
                }
            };

            return chain.filter(exchange.mutate().response(decoratedResponse).build());
        };
    }

    private boolean isTextContent(HttpHeaders headers) {
        MediaType contentType = headers.getContentType();
        if (contentType == null) {
            return false;
        }
        return contentType.isCompatibleWith(MediaType.APPLICATION_JSON)
            || contentType.getType().equalsIgnoreCase("text");
    }

    public static class Config {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
