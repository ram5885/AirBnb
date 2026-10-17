package com.HotelManagement.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The token's account must still exist with the same email: guards against a token outliving its user (e.g.
 * after a database reset reuses ids). The monolith's CurrentUserService did this on every request against the
 * shared user table; here auth-service answers and the answer is cached briefly per user. If auth-service can't be
 * asked, the request is refused with 503 (fail closed).
 */
@Component
public class SessionCheckFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(SessionCheckFilter.class);
    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    record UserView(Long id, String email) {
    }

    private record Cached(String email, Instant until) {
    }

    private final WebClient authClient;
    private final Duration ttl;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    public SessionCheckFilter(@Value("${app.auth-service-url}") String authServiceUrl,
                              @Value("${app.internal-api-token}") String internalToken,
                              @Value("${app.session-check-ttl-seconds:60}") long ttlSeconds) {
        if (internalToken == null || internalToken.length() < 16) {
            throw new IllegalStateException("app.internal-api-token (INTERNAL_API_TOKEN) must be at least 16 characters");
        }
        this.authClient = WebClient.builder().baseUrl(authServiceUrl).defaultHeader("X-Internal-Token", internalToken).build();
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Mono<Boolean> sessionValid = ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .flatMap(a -> {
                    Jwt jwt = ((JwtAuthenticationToken) a).getToken();
                    exchange.getAttributes().put(RequestLoggingWebFilter.USER_ATTRIBUTE, jwt.getSubject());
                    return check(jwt);
                })
                .defaultIfEmpty(true); // anonymous request (public path): nothing to check
        return sessionValid
                .onErrorResume(AuthUnavailable.class, e -> JsonErrors.write(exchange.getResponse(),
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "Account information is temporarily unavailable. Please try again in a moment.")
                        .then(Mono.empty()))
                .flatMap(valid -> {
                    if (valid) {
                        return chain.filter(exchange);
                    }
                    Object requestId = exchange.getAttribute(RequestLoggingWebFilter.REQUEST_ID_ATTRIBUTE);
                    Object userId = exchange.getAttribute(RequestLoggingWebFilter.USER_ATTRIBUTE);
                    log.info("req={} Rejected token of user {}: the account no longer exists or its email changed", requestId, userId);
                    audit.info("req={} event=SESSION_REJECTED outcome=FAILURE actor={} reason=\"account not found\"", requestId, userId);
                    return JsonErrors.write(exchange.getResponse(), HttpStatus.UNAUTHORIZED,
                            "Your session has expired. Please log in again.");
                });
    }

    private Mono<Boolean> check(Jwt jwt) {
        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            return Mono.just(false);
        }
        String email = jwt.getClaimAsString("email");
        Cached cached = cache.get(userId);
        if (cached != null && cached.until().isAfter(Instant.now())) {
            return Mono.just(Objects.equals(cached.email(), email));
        }
        log.debug("Checking with auth-service that user {} still exists", userId);
        return authClient.get().uri("/internal/users/{id}", userId).retrieve().bodyToMono(UserView.class)
                .timeout(Duration.ofSeconds(5))
                .map(user -> {
                    cache.put(userId, new Cached(user.email(), Instant.now().plus(ttl)));
                    return Objects.equals(user.email(), email);
                })
                .onErrorResume(WebClientResponseException.NotFound.class, e -> {
                    cache.remove(userId);
                    return Mono.just(false);
                })
                .onErrorMap(e -> !(e instanceof AuthUnavailable), e -> {
                    log.warn("Session check for user {} failed: {}", userId, e.toString());
                    return new AuthUnavailable();
                });
    }

    static final class AuthUnavailable extends RuntimeException {
        AuthUnavailable() {
            super("auth-service unavailable", null, false, false);
        }
    }
}
