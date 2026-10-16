package com.HotelManagement.AirBnb;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hotel reviews: only the guest of a completed stay (CONFIRMED, payment complete, check-out reached) can
 * review it, once; public reading with summary, sorting and paging. Bookings are made through the real
 * API (reserve, pay at hotel, payment collected by the manager) and then moved into the past with SQL,
 * since the API rightly refuses past dates.
 */
class ReviewIntegrationTest extends ApiTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    Account manager;
    long hotel;
    long room;

    @BeforeEach
    void setUp() throws Exception {
        manager = signup("HOTEL_MANAGER");
        hotel = newHotel(manager, "Kerala", "Alappuzha");
        room = newRoom(manager, hotel, 10);
    }

    // ---------------------------------------------------------------- helpers

    long confirmedBooking(Account guest, long hotelId, long roomId, boolean collectPayment) throws Exception {
        String g = mvc.perform(auth(post("/api/v1/guests"), guest).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Asha\",\"gender\":\"FEMALE\",\"age\":30}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long guestId = ((Number) JsonPath.read(g, "$.id")).longValue();
        String b = mvc.perform(auth(post("/api/v1/bookings"), guest).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hotelId":%d,"roomId":%d,"checkInDate":"%s","checkOutDate":"%s","roomsCount":1,"guestIds":[%d]}"""
                                .formatted(hotelId, roomId, TOMORROW, TOMORROW.plusDays(2), guestId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long bookingId = ((Number) JsonPath.read(b, "$.id")).longValue();
        mvc.perform(auth(post("/api/v1/payments/" + bookingId + "/pay-at-hotel"), guest)).andExpect(status().isOk());
        if (collectPayment) {
            mvc.perform(auth(patch("/api/v1/admin/bookings/" + bookingId + "/payment-collected"), manager))
                    .andExpect(status().isOk());
        }
        return bookingId;
    }

    /** Moves the stay so it ended {@code daysAgo} days ago (0 = checks out today). */
    void endStay(long bookingId, int daysAgo) {
        LocalDate out = LocalDate.now().minusDays(daysAgo);
        jdbc.update("update booking set check_in_date = ?, check_out_date = ? where booking_id = ?",
                out.minusDays(2), out, bookingId);
    }

    long completedStay(Account guest) throws Exception {
        long bookingId = confirmedBooking(guest, hotel, room, true);
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
                .andExpect(jsonPath("$.roomType").exists());

        review(guest, hotel, bookingId, 4, "Trying to review the same stay twice.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("You've already reviewed this stay"));
        summary(hotel).andExpect(jsonPath("$.totalReviews").value(1));

        mvc.perform(auth(get("/api/v1/hotels/" + hotel + "/reviews/eligibility"), guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookings[0].bookingId").value(bookingId))
                .andExpect(jsonPath("$.bookings[0].status").value("REVIEWED"))
                .andExpect(jsonPath("$.bookings[0].review.rating").value(5));
    }

    @Test
    void checkOutDayCountsAsCompleted() throws Exception {
        Account guest = signup("GUEST");
        long bookingId = confirmedBooking(guest, hotel, room, true);
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

        long otherHotel = newHotel(manager, "Kerala", "Alappuzha");
        newRoom(manager, otherHotel, 2);
        review(owner, otherHotel, bookingId, 5, "Using a booking from another hotel.")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("This booking is for a different hotel"));
        summary(otherHotel).andExpect(jsonPath("$.totalReviews").value(0));
    }

    @Test
    void onlyCompletedPaidStaysQualify() throws Exception {
        Account guest = signup("GUEST");

        long upcoming = confirmedBooking(guest, hotel, room, true); // confirmed and paid, but in the future
        review(guest, hotel, upcoming, 5, "Reviewing before I have even arrived.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("You can review this stay after check-out on " + TOMORROW.plusDays(2)));

        long unpaid = confirmedBooking(guest, hotel, room, false); // pay at hotel, never collected
        endStay(unpaid, 1);
        review(guest, hotel, unpaid, 5, "Payment was never collected here.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("This stay can be reviewed once its payment is complete"));

        // (a payment collected at the hotel can't be cancelled in the app, so cancel before collection)
        long cancelled = confirmedBooking(guest, hotel, room, false);
        mvc.perform(auth(patch("/api/v1/admin/bookings/" + cancelled + "/cancel"), manager)).andExpect(status().isOk());
        endStay(cancelled, 1);
        review(guest, hotel, cancelled, 5, "This booking was cancelled earlier.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Only completed stays can be reviewed (this booking is CANCELLED)"));

        String reserved = mvc.perform(auth(post("/api/v1/bookings"), guest).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hotelId":%d,"roomId":%d,"checkInDate":"%s","checkOutDate":"%s","roomsCount":1}"""
                                .formatted(hotel, room, TOMORROW, TOMORROW.plusDays(1))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long reservedId = ((Number) JsonPath.read(reserved, "$.id")).longValue();
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

        mvc.perform(get("/api/v1/hotels/" + hotel + "/reviews").param("sort", "best")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/hotels/" + hotel + "/reviews").param("size", "51")).andExpect(status().isBadRequest());
    }

    @Test
    void inactiveHotelsReviewsAreNotPublic() throws Exception {
        mvc.perform(auth(patch("/api/v1/admin/hotels/" + hotel + "/status"), manager)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isOk());
        summary(hotel).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/hotels/" + hotel + "/reviews")).andExpect(status().isNotFound());
    }
}
