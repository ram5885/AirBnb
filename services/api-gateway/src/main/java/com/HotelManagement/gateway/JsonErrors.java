package com.HotelManagement.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/** Writes the {"error": "..."} body every service (and the React app's api.js) uses. */
final class JsonErrors {

    private static final Logger log = LoggerFactory.getLogger(JsonErrors.class);

    private JsonErrors() {
    }

    static Mono<Void> write(ServerHttpResponse response, HttpStatus status, String message) {
        if (response.isCommitted()) {
            log.debug("Response already committed; can't send {} {}", status.value(), message);
            return Mono.empty();
        }
        log.debug("Responding {}: {}", status.value(), message);
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String json = "{\"error\":\"" + message.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
