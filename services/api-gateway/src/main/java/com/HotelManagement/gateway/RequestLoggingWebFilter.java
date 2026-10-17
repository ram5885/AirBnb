package com.HotelManagement.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * First filter of every request at the edge (before security): takes the request id from nginx (X-Request-Id) or
 * creates one, forwards it to the services (so every service logs the same id), returns it to the client, and writes
 * one access-log line per request (logger "ACCESS" → access.log). Requests the gateway itself refuses (no/invalid
 * token, wrong role) also go to the audit log (logger "AUDIT"); refusals by a service are audited by that service. Reactive code has no per-thread MDC, so the id is written into each line explicitly.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingWebFilter implements WebFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    /** Exchange attributes read by other gateway components for their log lines. */
    public static final String REQUEST_ID_ATTRIBUTE = "gateway.requestId";
    public static final String USER_ATTRIBUTE = "gateway.userId";
    /** Set when the gateway itself refused the request (audit event name); services audit their own refusals. */
    public static final String REJECTED_ATTRIBUTE = "gateway.rejected";

    private static final Logger access = LoggerFactory.getLogger("ACCESS");
    private static final Logger audit = LoggerFactory.getLogger("AUDIT");
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String supplied = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        String requestId = supplied != null && SAFE_ID.matcher(supplied).matches() ? supplied : UUID.randomUUID().toString();
        exchange.getAttributes().put(REQUEST_ID_ATTRIBUTE, requestId);
        ServerWebExchange traced = exchange.mutate()
                .request(r -> r.headers(h -> h.set(REQUEST_ID_HEADER, requestId)))
                .build();
        traced.getResponse().beforeCommit(() -> {
            traced.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
            return Mono.empty();
        });
        long start = System.nanoTime();
        String path = request.getPath().value();
        boolean health = path.startsWith("/actuator/health");
        return chain.filter(traced).doFinally(signal -> {
            if (health) {
                return;
            }
            HttpStatusCode status = traced.getResponse().getStatusCode();
            int code = status == null ? 200 : status.value();
            Object user = exchange.getAttribute(USER_ATTRIBUTE);
            String query = request.getURI().getRawQuery();
            String ip = clientIp(request);
            access.info("req={} ip={} user={} \"{} {}{}\" status={} {}ms{}", requestId, ip, user == null ? "-" : user,
                    request.getMethod(), path, query == null ? "" : "?" + (query.length() > 300 ? query.substring(0, 300) + "..." : query),
                    code, (System.nanoTime() - start) / 1_000_000, signal == reactor.core.publisher.SignalType.CANCEL ? " (client aborted)" : "");
            Object rejected = exchange.getAttribute(REJECTED_ATTRIBUTE);
            if (rejected != null) {
                audit.info("req={} event={} outcome=FAILURE actor={} ip={} method={} path={}", requestId, rejected,
                        user == null ? "anonymous" : user, ip, request.getMethod(), path);
            }
        });
    }

    private static String clientIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        InetSocketAddress remote = request.getRemoteAddress();
        return remote == null ? "-" : remote.getAddress().getHostAddress();
    }
}
