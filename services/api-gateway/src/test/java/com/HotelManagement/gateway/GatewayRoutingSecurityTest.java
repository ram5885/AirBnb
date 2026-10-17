package com.HotelManagement.gateway;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The gateway in front of stub services (one tiny HTTP server per service that echoes who answered). Checks the
 * route table, the monolith's role rules, header hygiene, the account-still-exists check and error handling.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.jwt.secret=" + GatewayRoutingSecurityTest.SECRET,
        "app.internal-api-token=test-internal-token-0123456789",
        "app.session-check-ttl-seconds=0",
        "app.logging.dir=target/logs"
})
@AutoConfigureWebTestClient
class GatewayRoutingSecurityTest {

    static final String SECRET = "test-only-jwt-secret-0123456789abcdef-0123456789";
    static final long DELETED_USER = 666;

    static final HttpServer AUTH = stub("auth");
    static final HttpServer CATALOG = stub("catalog");
    static final HttpServer REVIEW = stub("review");

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("AUTH_SERVICE_URL", () -> url(AUTH));
        registry.add("app.auth-service-url", () -> url(AUTH));
        registry.add("CATALOG_SERVICE_URL", () -> url(CATALOG));
        registry.add("REVIEW_SERVICE_URL", () -> url(REVIEW));
        registry.add("BOOKING_SERVICE_URL", () -> "http://127.0.0.1:1"); // nothing listens: service down
    }

    @AfterAll
    static void stop() {
        AUTH.stop(0);
        CATALOG.stop(0);
        REVIEW.stop(0);
    }

    @Autowired
    WebTestClient client;

    /** Answers {"service": name, "path": ..., "internalToken": ..., "auth": bool}; auth also serves /internal/users/{id}. */
    static HttpServer stub(String name) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                int status = 200;
                String body;
                if (name.equals("auth") && path.startsWith("/internal/users/")) {
                    long id = Long.parseLong(path.substring("/internal/users/".length()));
                    if (id == DELETED_USER) {
                        status = 404;
                        body = "{\"error\":\"User not found\"}";
                    } else {
                        body = "{\"id\":%d,\"name\":\"Test\",\"email\":\"user%d@test.com\"}".formatted(id, id);
                    }
                } else {
                    body = "{\"service\":\"%s\",\"path\":\"%s\",\"internalToken\":%s,\"auth\":%s,\"requestId\":\"%s\"}".formatted(name, path,
                            exchange.getRequestHeaders().getFirst("X-Internal-Token") == null ? "null" : "\"present\"",
                            exchange.getRequestHeaders().getFirst("Authorization") != null,
                            exchange.getRequestHeaders().getFirst("X-Request-Id"));
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    static String token(long userId, String role) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(
                new ImmutableSecret<>(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(String.valueOf(userId)).issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS)).claim("email", "user" + userId + "@test.com")
                .claim("roles", List.of(role)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @Test
    void publicPathsAreRoutedWithoutAToken() {
        client.get().uri("/api/v1/hotels/search?city=Wai").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.service").isEqualTo("catalog");
        client.get().uri("/api/locations/states").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.service").isEqualTo("catalog");
        client.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.service").isEqualTo("auth");
    }

    @Test
    void reviewPathsGoToReviewServiceNotCatalog() {
        client.get().uri("/api/v1/hotels/7/reviews").exchange()
                .expectBody().jsonPath("$.service").isEqualTo("review");
        client.get().uri("/api/v1/hotels/7/reviews/summary").exchange()
                .expectBody().jsonPath("$.service").isEqualTo("review");
        client.get().uri("/api/v1/hotels/7").exchange()
                .expectBody().jsonPath("$.service").isEqualTo("catalog");
    }

    @Test
    void roleRulesAreEnforcedAtTheEdge() {
        client.get().uri("/api/v1/admin/hotels").exchange()
                .expectStatus().isUnauthorized().expectBody().jsonPath("$.error").isEqualTo("Please log in to continue");
        client.get().uri("/api/v1/admin/hotels").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt").exchange()
                .expectStatus().isUnauthorized();
        client.get().uri("/api/v1/admin/hotels").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(11, "GUEST")).exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.error").isEqualTo("You are not allowed to perform this action");
        client.get().uri("/api/v1/admin/hotels").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(12, "HOTEL_MANAGER"))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.service").isEqualTo("catalog").jsonPath("$.auth").isEqualTo(true);
        client.post().uri("/api/v1/hotels/7/reviews").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(13, "HOTEL_MANAGER"))
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange().expectStatus().isForbidden();
        client.post().uri("/api/v1/hotels/7/reviews").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(14, "GUEST"))
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.service").isEqualTo("review");
    }

    @Test
    void clientSuppliedInternalTokensAreStrippedAndInternalPathsAreClosed() {
        client.get().uri("/api/v1/hotels/7").header("X-Internal-Token", "test-internal-token-0123456789").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.internalToken").doesNotExist();
        client.get().uri("/internal/users/1").exchange().expectStatus().isUnauthorized(); // anonymous: never routed
        client.get().uri("/internal/users/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(15, "HOTEL_MANAGER"))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void tokensOfDeletedAccountsAreRejected() {
        client.get().uri("/api/v1/admin/hotels").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(DELETED_USER, "HOTEL_MANAGER"))
                .exchange().expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.error").isEqualTo("Your session has expired. Please log in again.");
    }

    @Test
    void downstreamOutageIsAJson503() {
        client.get().uri("/api/v1/bookings").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(16, "GUEST")).exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.error")
                .isEqualTo("This part of the site is temporarily unavailable. Please try again in a moment.");
        // Razorpay webhooks are public at the edge (booking-service checks their HMAC signature).
        client.post().uri("/api/v1/payments/webhook").contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void unknownPathsAreJson404() {
        client.get().uri("/api/nothing-here").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(17, "GUEST")).exchange()
                .expectStatus().isNotFound().expectBody().jsonPath("$.error").isEqualTo("Not found");
    }

    @Test
    void corsAllowsTheConfiguredFrontendOrigins() {
        client.options().uri("/api/v1/hotels/search")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000");
        client.options().uri("/api/v1/hotels/search")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void requestIdIsCreatedOrKeptAndForwardedToTheService() {
        client.get().uri("/api/v1/hotels/7").exchange()
                .expectHeader().exists("X-Request-Id");
        client.get().uri("/api/v1/hotels/7").header("X-Request-Id", "nginx-abc123").exchange()
                .expectHeader().valueEquals("X-Request-Id", "nginx-abc123")
                .expectBody().jsonPath("$.requestId").isEqualTo("nginx-abc123");
        client.get().uri("/api/v1/hotels/7").header("X-Request-Id", "bad id <script>").exchange()
                .expectHeader().value("X-Request-Id", id -> org.assertj.core.api.Assertions.assertThat(id).doesNotContain("bad"));
    }

    @Test
    void healthIsPublic() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }
}
