package com.HotelManagement.AirBnb;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared helpers for catalog-service API tests: real HTTP layer, security filter chain and PostgreSQL.
 * Accounts are JWTs signed with the test secret (auth-service issues them in production); bookings are
 * represented by inventory holds created through the internal API, exactly as booking-service does.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestFlywayConfig.class)
abstract class ApiTestSupport {

    static final String JWT_SECRET = "test-only-jwt-secret-0123456789abcdef-0123456789";
    static final String INTERNAL_TOKEN = "test-internal-token-0123456789";
    private static final AtomicLong USER_IDS = new AtomicLong(System.nanoTime() % 1_000_000_000L);
    private static final NimbusJwtEncoder ENCODER = new NimbusJwtEncoder(
            new ImmutableSecret<>(new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));

    @Autowired
    protected MockMvc mvc;

    static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    // ---------------------------------------------------------------- accounts

    record Account(long id, String token, String email) {
    }

    /** A fresh user with a valid token for the role (as auth-service would issue at signup). */
    Account signup(String role) {
        long id = USER_IDS.incrementAndGet();
        String email = role.toLowerCase() + "-" + id + "@test.com";
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

    // ---------------------------------------------------------------- hotels & rooms

    static String hotelJson(String name, String state, String city, String extra) {
        return """
                {"name":"%s","state":"%s","city":"%s","address":"12 MG Road","contactPerson":"Asha Rao",
                 "phoneNumber":"9876543210","email":"Front.Desk@Hotel.com","amenities":["WIFI","SWIMMING_POOL","WIFI"],
                 "active":true%s}""".formatted(name, state, city, extra);
    }

    static MockMultipartFile data(String json) {
        return new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes());
    }

    static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    static MockMultipartFile photo(String name) throws Exception {
        return new MockMultipartFile("photos", name, "image/png", png());
    }

    ResultActions createHotel(Account manager, String json, MockMultipartFile... photos) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/v1/admin/hotels").file(data(json));
        for (MockMultipartFile p : photos) {
            req.file(p);
        }
        return mvc.perform(auth(req, manager));
    }

    long newHotel(Account manager) throws Exception {
        return newHotel(manager, "Kerala", "Alappuzha");
    }

    long newHotel(Account manager, String state, String city) throws Exception {
        String body = createHotel(manager, hotelJson("Hotel " + UUID.randomUUID(), state, city, ""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    static String roomJson(String type, String price, int total, int capacity) {
        return """
                {"type":"%s","basePrice":%s,"totalCount":%d,"capacity":%d,"amenities":["AIR_CONDITIONING"]}"""
                .formatted(type, price, total, capacity);
    }

    long newRoom(Account manager, long hotelId, int totalCount) throws Exception {
        String body = mvc.perform(auth(multipart("/api/v1/admin/hotels/" + hotelId + "/rooms")
                        .file(data(roomJson("Deluxe " + UUID.randomUUID(), "2500", totalCount, 2))), manager))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    ResultActions inventory(Account manager, long hotelId, long roomId, LocalDate start, LocalDate end) throws Exception {
        return mvc.perform(auth(get("/api/v1/admin/hotels/" + hotelId + "/rooms/" + roomId + "/inventory")
                .param("startDate", start.toString()).param("endDate", end.toString()), manager));
    }

    // ---------------------------------------------------------------- inventory holds (what booking-service calls)

    /** Takes rooms for a stay the way booking-service does when a guest books (200 = held). */
    ResultActions book(long hotelId, long roomId, LocalDate in, LocalDate out, int rooms) throws Exception {
        return hold(UUID.randomUUID(), hotelId, roomId, in, out, rooms, 0);
    }

    ResultActions hold(UUID key, long hotelId, long roomId, LocalDate in, LocalDate out, int rooms, int guests) throws Exception {
        return mvc.perform(internal(post("/internal/inventory/holds")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"holdKey":"%s","hotelId":%d,"roomId":%d,"checkInDate":"%s","checkOutDate":"%s","roomsCount":%d,"guestCount":%d}"""
                        .formatted(key, hotelId, roomId, in, out, rooms, guests)));
    }

    /** A held booking for tomorrow, 2 nights; returns its hold key. */
    UUID newBooking(long hotelId, long roomId, int rooms) throws Exception {
        UUID key = UUID.randomUUID();
        hold(key, hotelId, roomId, TOMORROW, TOMORROW.plusDays(2), rooms, 0).andExpect(status().isOk());
        return key;
    }

    /** Releases a hold, as booking-service does after a cancellation or expiry. */
    ResultActions release(UUID key) throws Exception {
        return mvc.perform(internal(post("/internal/inventory/holds/" + key + "/release")));
    }
}
