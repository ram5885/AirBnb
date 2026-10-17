package com.HotelManagement.AirBnb;

import com.HotelManagement.AirBnb.client.AuthClient;
import com.HotelManagement.AirBnb.client.BookingClient.StayView;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hotel reviews: only the guest of a completed stay (CONFIRMED, payment complete, check-out reached) can
 * review it, once; public reading with summary, sorting and paging. Ported from the monolith: the booking facts
 * come from booking-service (a fake here, set to the states the monolith test reached through the booking API),
 * hotel visibility from catalog-service, and each new review pushes the hotel's rating to catalog.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestFlywayConfig.class, Fakes.class})
class ReviewIntegrationTest {

    static final String JWT_SECRET = "test-only-jwt-secret-0123456789abcdef-0123456789";
    static final LocalDate TOMORROW = LocalDate.now().plusDays(1);
    private static final AtomicLong IDS = new AtomicLong(System.nanoTime() % 1_000_000_000L);
    private static final NimbusJwtEncoder ENCODER = new NimbusJwtEncoder(
            new ImmutableSecret<>(new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));

    @Autowired
    MockMvc mvc;

    @Autowired
    Fakes.FakeCatalog catalog;

    @Autowired
    Fakes.FakeBookings bookings;

    @Autowired
    Fakes.FakeAuth users;

    record Account(long id, String token, String email) {
    }

    Account manager;
    long hotel;

    @BeforeEach
    void setUp() {
        manager = signup("HOTEL_MANAGER");
        hotel = newHotel();
    }

    // ---------------------------------------------------------------- helpers

    Account signup(String role) {
        long id = IDS.incrementAndGet();
        String email = role.toLowerCase() + "-" + id + "@test.com";
        users.users.put(id, new AuthClient.UserView(id, "Test " + role, email));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(String.valueOf(id)).issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS)).claim("email", email).claim("roles", List.of(role)).build();
        return new Account(id, ENCODER.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue(), email);
    }

    static <T extends AbstractMockHttpServletRequestBuilder<?>> T auth(T builder, Account account) {
        builder.header("Authorization", "Bearer " + account.token());
        return builder;
    }

    long newHotel() {
        long id = IDS.incrementAndGet();
        catalog.publicHotels.add(id);
        return id;
    }

    /** A booking in booking-service: confirmed through pay at hotel (payment collected or not), stay tomorrow. */
    long confirmedBooking(Account guest, long hotelId, boolean collectPayment) {
        return booking(guest, hotelId, "CONFIRMED", collectPayment ? "CONFIRMED" : "PENDING", TOMORROW, TOMORROW.plusDays(2));
    }

    long booking(Account guest, long hotelId, String status, String paymentStatus, LocalDate in, LocalDate out) {
        long id = IDS.incrementAndGet();
        bookings.stays.put(id, new StayView(id, guest.id(), hotelId, "Deluxe King", in, out, status, paymentStatus));
        return id;
    }

    /** Moves the stay so it ended {@code daysAgo} days ago (0 = checks out today). */
    void endStay(long bookingId, int daysAgo) {
        LocalDate out = LocalDate.now().minusDays(daysAgo);
        StayView s = bookings.stays.get(bookingId);
        bookings.stays.put(bookingId, new StayView(s.bookingId(), s.userId(), s.hotelId(), s.roomType(),
                out.minusDays(2), out, s.status(), s.paymentStatus()));
    }

    long completedStay(Account guest) {
        long bookingId = confirmedBooking(guest, hotel, true);
        endStay(bookingId, 1);
        return bookingId;
    }

    ResultActions review(Account guest, long hotelId, long bookingId, Object rating, String comment) throws Exception {
        String body = "{\"bookingId\":%d,\"rating\":%s,\"comment\":%s}".formatted(bookingId, rating,
                comment == null ? "null" : "\"" + comment + "\"");
        var req = post("/api/v1/hotels/" + hotelId + "/reviews").contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(guest == null ? req : auth(req, guest));
    }

    ResultActions summary(long hotelId) throws Exception {
        return mvc.perform(get("/api/v1/hotels/" + hotelId + "/reviews/summary"));
    }

    // ---------------------------------------------------------------- submitting

    @Test
    void guestWithACompletedStayCanReviewItOnce() throws Exception {
        Account guest = signup("GUEST");
        long bookingId = completedStay(guest);

        review(guest, hotel, bookingId, 5, "  Spotless room and very helpful staff.  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Spotless room and very helpful staff."))
                .andExpect(jsonPath("$.reviewerName").value("Test")) // first name of "Test GUEST" only
                .andExpect(jsonPath("$.verifiedStay").value(true))
                .andExpect(jsonPath("$.roomType").value("Deluxe King"));

        review(guest, hotel, bookingId, 4, "Trying to review the same stay twice.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("You've already reviewed this stay"));
        summary(hotel).andExpect(jsonPath("$.totalReviews").value(1));

        mvc.perform(auth(get("/api/v1/hotels/" + hotel + "/reviews/eligibility"), guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookings[0].bookingId").value(bookingId))
                .andExpect(jsonPath("$.bookings[0].status").value("REVIEWED"))
                .andExpect(jsonPath("$.bookings[0].review.rating").value(5));

        // Guest search's rating filter (catalog-service) got the hotel's new rating.
        assertThat(catalog.pushed.get(hotel).ratingSum()).isEqualTo(5);
        assertThat(catalog.pushed.get(hotel).ratingCount()).isEqualTo(1);
    }

    @Test
    void checkOutDayCountsAsCompleted() throws Exception {
        Account guest = signup("GUEST");
        long bookingId = confirmedBooking(guest, hotel, true);
        endStay(bookingId, 0);
        review(guest, hotel, bookingId, 4, "Checked out this morning, lovely stay.").andExpect(status().isCreated());
    }

    @Test
    void requiresALoggedInGuest() throws Exception {
        long bookingId = completedStay(signup("GUEST"));
        review(null, hotel, bookingId, 5, "Anonymous reviews are not allowed.").andExpect(status().isUnauthorized());
        review(manager, hotel, bookingId, 5, "Managers can't post guest reviews.").andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/hotels/" + hotel + "/reviews/eligibility")).andExpect(status().isUnauthorized());
        summary(hotel).andExpect(jsonPath("$.totalReviews").value(0));
    }

    @Test
    void cannotReviewSomeoneElsesBookingOrAnotherHotel() throws Exception {
        Account owner = signup("GUEST");
        long bookingId = completedStay(owner);
        review(signup("GUEST"), hotel, bookingId, 1, "Not my booking but trying anyway.")
                .andExpect(status().isNotFound());

        long otherHotel = newHotel();
        review(owner, otherHotel, bookingId, 5, "Using a booking from another hotel.")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("This booking is for a different hotel"));
        summary(otherHotel).andExpect(jsonPath("$.totalReviews").value(0));
    }

    @Test
    void onlyCompletedPaidStaysQualify() throws Exception {
        Account guest = signup("GUEST");

        long upcoming = confirmedBooking(guest, hotel, true); // confirmed and paid, but in the future
        review(guest, hotel, upcoming, 5, "Reviewing before I have even arrived.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("You can review this stay after check-out on " + TOMORROW.plusDays(2)));

        long unpaid = confirmedBooking(guest, hotel, false); // pay at hotel, never collected
        endStay(unpaid, 1);
        review(guest, hotel, unpaid, 5, "Payment was never collected here.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("This stay can be reviewed once its payment is complete"));

        long cancelled = booking(guest, hotel, "CANCELLED", "CANCELLED", TOMORROW, TOMORROW.plusDays(2));
        endStay(cancelled, 1);
        review(guest, hotel, cancelled, 5, "This booking was cancelled earlier.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Only completed stays can be reviewed (this booking is CANCELLED)"));

        long reservedId = booking(guest, hotel, "RESERVED", null, TOMORROW, TOMORROW.plusDays(1));
        endStay(reservedId, 1);
        review(guest, hotel, reservedId, 5, "Only reserved, never confirmed.").andExpect(status().isConflict());

        // Eligibility lists confirmed bookings only, with the reason each can't be reviewed yet.
        mvc.perform(auth(get("/api/v1/hotels/" + hotel + "/reviews/eligibility"), guest))
                .andExpect(jsonPath("$.bookings", hasSize(2)))
                .andExpect(jsonPath("$.bookings[*].status", contains("UPCOMING", "NOT_ELIGIBLE")));
        summary(hotel).andExpect(jsonPath("$.totalReviews").value(0));
    }

    @Test
    void validatesRatingAndComment() throws Exception {
        Account guest = signup("GUEST");
        long bookingId = completedStay(guest);
        review(guest, hotel, bookingId, 0, "Rating below the allowed range.")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.rating").exists());
        review(guest, hotel, bookingId, 6, "Rating above the allowed range.")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.rating").exists());
        review(guest, hotel, bookingId, null, "Rating is missing entirely.")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.rating").exists());
        review(guest, hotel, bookingId, 4, "    ")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.comment").exists());
        review(guest, hotel, bookingId, 4, "Too short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Write at least 10 characters about your stay"));
        review(guest, hotel, bookingId, 4, "x".repeat(1001))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.comment").exists());
        review(guest, hotel, 999_999_999L, 4, "This booking does not exist.").andExpect(status().isNotFound());
        review(guest, 999_999_999L, bookingId, 4, "This hotel does not exist.").andExpect(status().isNotFound());
        summary(hotel).andExpect(jsonPath("$.totalReviews").value(0));

        // Markup is stored as plain text (rendered escaped by the frontend), never interpreted.
        review(guest, hotel, bookingId, 3, "<script>alert(1)</script> was in my review")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comment").value("<script>alert(1)</script> was in my review"));
    }

    @Test
    void concurrentSubmissionsForOneBookingCreateOneReview() throws Exception {
        Account guest = signup("GUEST");
        long bookingId = completedStay(guest);
        Callable<Integer> submit = () -> review(guest, hotel, bookingId, 5, "Submitted twice at the same time.")
                .andReturn().getResponse().getStatus();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = pool.invokeAll(List.of(submit, submit));
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdown();
        }
        summary(hotel).andExpect(jsonPath("$.totalReviews").value(1));
    }

    // ---------------------------------------------------------------- reading

    @Test
    void summarySortingAndPaging() throws Exception {
        summary(hotel)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(nullValue()))
                .andExpect(jsonPath("$.totalReviews").value(0))
                .andExpect(jsonPath("$.ratingDistribution.5").value(0));
        mvc.perform(get("/api/v1/hotels/" + hotel + "/reviews"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));

        Account guest = signup("GUEST");
        int[] ratings = {5, 4, 4, 2};
        for (int r : ratings) {
            review(guest, hotel, completedStay(guest), r, "A stay rated " + r + " out of five.").andExpect(status().isCreated());
        }

        summary(hotel)
                .andExpect(jsonPath("$.totalReviews").value(4))
                .andExpect(jsonPath("$.averageRating").value(3.8)) // 15 / 4 = 3.75, rounded half up
                .andExpect(jsonPath("$.ratingDistribution.5").value(1))
                .andExpect(jsonPath("$.ratingDistribution.4").value(2))
                .andExpect(jsonPath("$.ratingDistribution.3").value(0))
                .andExpect(jsonPath("$.ratingDistribution.2").value(1))
                .andExpect(jsonPath("$.ratingDistribution.1").value(0));
        assertThat(catalog.pushed.get(hotel).ratingSum()).isEqualTo(15);
        assertThat(catalog.pushed.get(hotel).ratingCount()).isEqualTo(4);

        mvc.perform(get("/api/v1/hotels/" + hotel + "/reviews").param("sort", "recent"))
                .andExpect(jsonPath("$.content[*].rating", contains(2, 4, 4, 5)));
        mvc.perform(get("/api/v1/hotels/" + hotel + "/reviews").param("sort", "highest"))
                .andExpect(jsonPath("$.content[*].rating", contains(5, 4, 4, 2)));
        mvc.perform(get("/api/v1/hotels/" + hotel + "/reviews").param("sort", "lowest"))
                .andExpect(jsonPath("$.content[*].rating", contains(2, 4, 4, 5)));
        mvc.perform(get("/api/v1/hotels/" + hotel + "/reviews").param("sort", "highest").param("size", "3").param("page", "1"))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[*].rating", contains(2)));

        // Public cards carry no private account data.
        String page = mvc.perform(get("/api/v1/hotels/" + hotel + "/reviews"))
                .andReturn().getResponse().getContentAsString();
        assertThat(page).doesNotContain(guest.email()).doesNotContain("\"email\"").doesNotContain("password");
        mvc.perform(get("/api/v1/hotels/" + hotel + "/reviews"))
                .andExpect(jsonPath("$.content[0].reviewerName").value("Test"))
                .andExpect(jsonPath("$.content[0].stayedOn").isNotEmpty());

        mvc.perform(get("/api/v1/hotels/" + hotel + "/reviews").param("sort", "best")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/hotels/" + hotel + "/reviews").param("size", "51")).andExpect(status().isBadRequest());
    }

    @Test
    void inactiveHotelsReviewsAreNotPublic() throws Exception {
        catalog.publicHotels.remove(hotel); // the manager deactivated it in catalog-service
        summary(hotel).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/hotels/" + hotel + "/reviews")).andExpect(status().isNotFound());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
