package com.HotelManagement.AirBnb;

import com.HotelManagement.AirBnb.client.AuthClient;
import com.HotelManagement.AirBnb.payment.gateway.PaymentGateway;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared helpers for booking-service API tests: real HTTP layer, security filter chain and PostgreSQL; the
 * Razorpay gateway is mocked (signatures are real), catalog-service and auth-service are in-memory fakes.
 * Rooms come from newRoom(): 2500/night, capacity 2.
 */
@SpringBootTest(properties = {
        // Test-only secrets: signatures in tests are computed with these. The gateway itself is mocked.
        "razorpay.key-secret=" + ApiTestSupport.TEST_KEY_SECRET,
        "razorpay.webhook-secret=" + ApiTestSupport.TEST_WEBHOOK_SECRET
})
@AutoConfigureMockMvc
@Import({TestFlywayConfig.class, FakeServicesConfig.class})
abstract class ApiTestSupport {

    static final String TEST_KEY_SECRET = "test_key_secret_for_unit_tests";
    static final String TEST_WEBHOOK_SECRET = "test_webhook_secret_for_unit_tests";
    static final String JWT_SECRET = "test-only-jwt-secret-0123456789abcdef-0123456789";
    static final String INTERNAL_TOKEN = "test-internal-token-0123456789";
    private static final AtomicLong USER_IDS = new AtomicLong(System.nanoTime() % 1_000_000_000L);
    private static final NimbusJwtEncoder ENCODER = new NimbusJwtEncoder(
            new ImmutableSecret<>(new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));

    /** No real Razorpay calls in automated tests. */
    @MockitoBean
    protected PaymentGateway gateway;

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected FakeCatalog catalog;

    @Autowired
    protected FakeAuth authService;

    static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    record Account(long id, String token, String email) {
    }

    /** A registered user (known to auth-service) with a valid token, as auth-service issues at signup. */
    Account signup(String role) {
        long id = USER_IDS.incrementAndGet();
        String email = role.toLowerCase() + "-" + id + "@test.com";
        authService.users.put(id, new AuthClient.UserView(id, "Test " + role, email));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(String.valueOf(id)).issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS)).claim("email", email).claim("roles", List.of(role)).build();
        String token = ENCODER.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new Account(id, token, email);
    }

    static <T extends AbstractMockHttpServletRequestBuilder<?>> T auth(T builder, Account account) {
        builder.header("Authorization", "Bearer " + account.token());
        return builder;
    }

    static <T extends AbstractMockHttpServletRequestBuilder<?>> T internal(T builder) {
        builder.header("X-Internal-Token", INTERNAL_TOKEN);
        return builder;
    }

    long newHotel(Account manager) {
        return catalog.addHotel(manager.id());
    }

    long newRoom(Account manager, long hotelId, int totalCount) {
        return catalog.addRoom(hotelId, totalCount);
    }
}
