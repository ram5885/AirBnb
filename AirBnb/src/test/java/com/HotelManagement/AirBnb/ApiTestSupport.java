package com.HotelManagement.AirBnb;

import com.HotelManagement.AirBnb.dto.booking.CreateBookingRequest;
import com.HotelManagement.AirBnb.entity.User;
import com.HotelManagement.AirBnb.payment.PaymentGateway;
import com.HotelManagement.AirBnb.exception.ConflictException;
import com.HotelManagement.AirBnb.repository.UserRepository;
import com.HotelManagement.AirBnb.service.BookingService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared helpers for API-level integration tests: real HTTP layer, security filter chain and
 * PostgreSQL database. Each helper creates fresh users (unique emails) so tests don't interfere.
 */
@SpringBootTest(properties = {
        // Test-only secrets: signatures in tests are computed with these. The gateway itself is mocked.
        "razorpay.key-secret=" + ApiTestSupport.TEST_KEY_SECRET,
        "razorpay.webhook-secret=" + ApiTestSupport.TEST_WEBHOOK_SECRET
})
@AutoConfigureMockMvc
abstract class ApiTestSupport {

    static final String TEST_KEY_SECRET = "test_key_secret_for_unit_tests";
    static final String TEST_WEBHOOK_SECRET = "test_webhook_secret_for_unit_tests";

    /** No real Razorpay calls in automated tests. */
    @MockitoBean
    protected PaymentGateway gateway;

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected BookingService bookingService;

    @Autowired
    protected UserRepository userRepository;

    static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    // ---------------------------------------------------------------- helpers

    record Account(long id, String token, String email) {
    }

    Account signup(String role) throws Exception {
        String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@test.com";
        String body = mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Test %s","email":"%s","password":"secret123","role":"%s"}""".formatted(role, email, role)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Account(((Number) JsonPath.read(body, "$.id")).longValue(), JsonPath.read(body, "$.token"), email);
    }

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

    static <T extends AbstractMockHttpServletRequestBuilder<?>> T auth(T builder, Account account) {
        builder.header("Authorization", "Bearer " + account.token());
        return builder;
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

    ResultActions book(Account guest, long hotelId, long roomId, LocalDate in, LocalDate out, int rooms) throws Exception {
        return mvc.perform(auth(post("/api/v1/bookings"), guest).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"hotelId":%d,"roomId":%d,"checkInDate":"%s","checkOutDate":"%s","roomsCount":%d}"""
                        .formatted(hotelId, roomId, in, out, rooms)));
    }

    long newBooking(Account guest, long hotelId, long roomId, int rooms) throws Exception {
        String body = book(guest, hotelId, roomId, TOMORROW, TOMORROW.plusDays(2), rooms)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    ResultActions inventory(Account manager, long hotelId, long roomId, LocalDate start, LocalDate end) throws Exception {
        return mvc.perform(auth(get("/api/v1/admin/hotels/" + hotelId + "/rooms/" + roomId + "/inventory")
                .param("startDate", start.toString()).param("endDate", end.toString()), manager));
    }
}
