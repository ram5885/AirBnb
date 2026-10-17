package com.HotelManagement.AirBnb;

import com.HotelManagement.AirBnb.booking.entity.HoldStatus;
import com.HotelManagement.AirBnb.booking.repository.BookingRepository;
import com.HotelManagement.AirBnb.booking.service.HoldMaintenanceJobs;
import com.HotelManagement.AirBnb.client.CatalogDtos.HoldRequest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Manager booking management (ported from the monolith's HotelManagerIntegrationTest.Bookings), the dashboard,
 * the internal API for review-service, and the cross-service failure handling of the hold protocol.
 */
class ManagerBookingAndResilienceTest extends ApiTestSupport {

    @Autowired
    BookingRepository bookingRepository;

    @Autowired
    HoldMaintenanceJobs jobs;

    @AfterEach
    void catalogBackUp() {
        catalog.unavailable = false;
        catalog.releaseUnavailable = false;
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

    @Nested
    class ManagerViews {

        @Test
        void managerSeesOnlyBookingsOfTheirHotels() throws Exception {
            Account m1 = signup("HOTEL_MANAGER");
            Account m2 = signup("HOTEL_MANAGER");
            Account guest = signup("GUEST");
            long h1 = newHotel(m1);
            long h2 = newHotel(m2);
            long b1 = newBooking(guest, h1, newRoom(m1, h1, 2), 1);
            newBooking(guest, h2, newRoom(m2, h2, 2), 1);

            mvc.perform(auth(get("/api/v1/admin/bookings"), m1))
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id").value(b1))
                    .andExpect(jsonPath("$.content[0].status").value("RESERVED"))
                    .andExpect(jsonPath("$.content[0].bookedBy").value("Test GUEST"));
            mvc.perform(auth(get("/api/v1/admin/bookings/" + b1), m2)).andExpect(status().isNotFound());
            mvc.perform(auth(patch("/api/v1/admin/bookings/" + b1 + "/accept"), m2)).andExpect(status().isNotFound());
            mvc.perform(auth(patch("/api/v1/admin/bookings/" + b1 + "/cancel"), m2)).andExpect(status().isNotFound());

            mvc.perform(auth(get("/api/v1/admin/bookings").param("status", "CANCELLED"), m1))
                    .andExpect(jsonPath("$.content", hasSize(0)));
            mvc.perform(auth(get("/api/v1/admin/bookings").param("startDate", TOMORROW.plusDays(10).toString()), m1))
                    .andExpect(jsonPath("$.content", hasSize(0)));
            mvc.perform(auth(get("/api/v1/admin/bookings").param("hotelId", String.valueOf(h1))
                            .param("startDate", TOMORROW.toString()).param("endDate", TOMORROW.toString()), m1))
                    .andExpect(jsonPath("$.content", hasSize(1)));
            mvc.perform(auth(get("/api/v1/admin/bookings/" + b1), m1))
                    .andExpect(jsonPath("$.bookedByEmail").value(guest.email()))
                    .andExpect(jsonPath("$.hotelCity").value("Alappuzha"));
        }

        @Test
        void acceptsReservedBookingOnce() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            Account guest = signup("GUEST");
            long hotelId = newHotel(manager);
            long bookingId = newBooking(guest, hotelId, newRoom(manager, hotelId, 2), 1);

            mvc.perform(auth(patch("/api/v1/admin/bookings/" + bookingId + "/accept"), manager))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));
            mvc.perform(auth(patch("/api/v1/admin/bookings/" + bookingId + "/accept"), manager))
                    .andExpect(status().isConflict());
        }

        @Test
        void cancelReleasesInventoryExactlyOnce() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            Account guest = signup("GUEST");
            long hotelId = newHotel(manager);
            long roomId = newRoom(manager, hotelId, 3);
            long bookingId = newBooking(guest, hotelId, roomId, 2);
            newBooking(guest, hotelId, roomId, 1);
            assertThat(catalog.bookedOn(roomId, TOMORROW)).isEqualTo(3);

            mvc.perform(auth(patch("/api/v1/admin/bookings/" + bookingId + "/accept"), manager)).andExpect(status().isOk());
            mvc.perform(auth(patch("/api/v1/admin/bookings/" + bookingId + "/cancel"), manager))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
            assertThat(catalog.bookedOn(roomId, TOMORROW)).isEqualTo(1);
            assertThat(catalog.bookedOn(roomId, TOMORROW.plusDays(1))).isEqualTo(1);
            assertThat(catalog.bookedOn(roomId, TOMORROW.plusDays(2))).isZero(); // check-out day was never booked

            mvc.perform(auth(patch("/api/v1/admin/bookings/" + bookingId + "/cancel"), manager))
                    .andExpect(status().isConflict());
            mvc.perform(auth(patch("/api/v1/admin/bookings/" + bookingId + "/accept"), manager))
                    .andExpect(status().isConflict());
            assertThat(catalog.bookedOn(roomId, TOMORROW)).isEqualTo(1);
            assertThat(bookingRepository.findForUser(bookingId, guest.id()).orElseThrow().getHoldStatus())
                    .isEqualTo(HoldStatus.RELEASED);
        }

        @Test
        void deletedHotelKeepsBookingHistoryAndRefusesAccept() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            Account guest = signup("GUEST");
            long hotelId = newHotel(manager);
            long roomId = newRoom(manager, hotelId, 2);
            long bookingId = newBooking(guest, hotelId, roomId, 1);
            catalog.deleteHotel(hotelId);

            mvc.perform(auth(get("/api/v1/admin/bookings/" + bookingId), manager))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hotelDeleted").value(true))
                    .andExpect(jsonPath("$.roomId").value(roomId));
            mvc.perform(auth(patch("/api/v1/admin/bookings/" + bookingId + "/accept"), manager))
                    .andExpect(status().isConflict());
            mvc.perform(auth(get("/api/v1/bookings/" + bookingId), guest))
                    .andExpect(jsonPath("$.canPay").value(false))
                    .andExpect(jsonPath("$.hotelPhotoUrl").doesNotExist());
            book(guest, hotelId, roomId, TOMORROW, TOMORROW.plusDays(1), 1).andExpect(status().isNotFound());
        }

        @Test
        void dashboardCombinesHotelAndBookingCounts() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            Account guest = signup("GUEST");
            long hotelId = newHotel(manager);
            newHotel(manager);
            long roomId = newRoom(manager, hotelId, 3);
            newBooking(guest, hotelId, roomId, 1);
            long cancelled = newBooking(guest, hotelId, roomId, 1);
            mvc.perform(auth(patch("/api/v1/bookings/" + cancelled + "/cancel"), guest)).andExpect(status().isOk());

            mvc.perform(auth(get("/api/v1/admin/dashboard"), manager))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalHotels").value(2))
                    .andExpect(jsonPath("$.activeHotels").value(2))
                    .andExpect(jsonPath("$.bookingsByStatus.RESERVED").value(1))
                    .andExpect(jsonPath("$.bookingsByStatus.CANCELLED").value(1))
                    .andExpect(jsonPath("$.bookingsByStatus.CONFIRMED").value(0));
            mvc.perform(auth(get("/api/v1/admin/dashboard"), guest)).andExpect(status().isForbidden());
        }
    }

    @Nested
    class Resilience {

        @Test
        void catalogOutageWhileBookingIs503AndNothingIsKept() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            Account guest = signup("GUEST");
            long hotelId = newHotel(manager);
            long roomId = newRoom(manager, hotelId, 2);
            catalog.unavailable = true;
            book(guest, hotelId, roomId, TOMORROW, TOMORROW.plusDays(1), 1)
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error").value("Hotel information is temporarily unavailable. Please try again in a moment."));
            catalog.unavailable = false;
            mvc.perform(auth(get("/api/v1/bookings"), guest)).andExpect(jsonPath("$.totalElements").value(0));
            assertThat(catalog.bookedOn(roomId, TOMORROW)).isZero();
        }

        @Test
        void myBookingsStillRenderFromSnapshotsWhenCatalogIsDown() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            Account guest = signup("GUEST");
            long hotelId = newHotel(manager);
            long bookingId = newBooking(guest, hotelId, newRoom(manager, hotelId, 2), 1);
            catalog.unavailable = true;
            mvc.perform(auth(get("/api/v1/bookings"), guest))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(bookingId))
                    .andExpect(jsonPath("$.content[0].hotelName").value("Hotel " + hotelId))
                    .andExpect(jsonPath("$.content[0].city").value("Alappuzha"));
            // Actions that depend on the hotel's current state refuse rather than guess.
            mvc.perform(auth(post("/api/v1/payments/" + bookingId + "/pay-at-hotel"), guest))
                    .andExpect(status().isServiceUnavailable());
        }

        @Test
        void failedReleaseKeepsRoomsBlockedUntilTheRetryJobReleasesThem() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            Account guest = signup("GUEST");
            long hotelId = newHotel(manager);
            long roomId = newRoom(manager, hotelId, 2);
            long bookingId = newBooking(guest, hotelId, roomId, 1);

            catalog.releaseUnavailable = true;
            mvc.perform(auth(patch("/api/v1/bookings/" + bookingId + "/cancel"), guest))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
            // Never oversell: the rooms stay taken until catalog confirms the release.
            assertThat(catalog.bookedOn(roomId, TOMORROW)).isEqualTo(1);
            assertThat(bookingRepository.findForUser(bookingId, guest.id()).orElseThrow().getHoldStatus())
                    .isEqualTo(HoldStatus.RELEASE_PENDING);

            catalog.releaseUnavailable = false;
            // The job only retries holds pending for 30s+; move the timestamp back instead of waiting.
            releaseOlderPending(bookingId);
            assertThat(catalog.bookedOn(roomId, TOMORROW)).isZero();
            assertThat(bookingRepository.findForUser(bookingId, guest.id()).orElseThrow().getHoldStatus())
                    .isEqualTo(HoldStatus.RELEASED);
        }

        @Autowired
        org.springframework.jdbc.core.JdbcTemplate jdbc;

        void releaseOlderPending(long bookingId) {
            jdbc.update("update booking set updated_at = now() - interval '5 minutes' where booking_id = ?", bookingId);
            jobs.retryPendingReleases();
        }

        @Test
        void orphanHoldsAreReleasedAndUnattachedOnesAttached() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            Account guest = signup("GUEST");
            long hotelId = newHotel(manager);
            long roomId = newRoom(manager, hotelId, 3);
            long bookingId = newBooking(guest, hotelId, roomId, 1);
            UUID bookingHold = bookingRepository.findForUser(bookingId, guest.id()).orElseThrow().getHoldKey();
            catalog.holds.get(bookingHold).bookingId = null; // as if the attach call had failed

            // A hold taken by a booking-service that died before saving the booking.
            UUID orphan = UUID.randomUUID();
            catalog.hold(new HoldRequest(orphan, hotelId, roomId, TOMORROW, TOMORROW.plusDays(2), 1, 0));
            assertThat(catalog.bookedOn(roomId, TOMORROW)).isEqualTo(2);

            jobs.reconcileUnattachedHolds();
            assertThat(catalog.holds.get(orphan).status).isEqualTo("RELEASED");
            assertThat(catalog.holds.get(bookingHold).bookingId).isEqualTo(bookingId);
            assertThat(catalog.bookedOn(roomId, TOMORROW)).isEqualTo(1);
        }

        @Test
        void deletedAccountsCanNotBook() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            Account guest = signup("GUEST");
            long hotelId = newHotel(manager);
            long roomId = newRoom(manager, hotelId, 2);
            authService.users.remove(guest.id());
            book(guest, hotelId, roomId, TOMORROW, TOMORROW.plusDays(1), 1)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Your session has expired. Please log in again."));
            assertThat(catalog.bookedOn(roomId, TOMORROW)).isZero();
        }
    }

    @Nested
    class InternalApi {

        @Test
        void reviewFactsAreScopedToTheUserAndNeedTheInternalToken() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            Account guest = signup("GUEST");
            long hotelId = newHotel(manager);
            long bookingId = newBooking(guest, hotelId, newRoom(manager, hotelId, 2), 1);

            mvc.perform(get("/internal/bookings/" + bookingId).param("userId", String.valueOf(guest.id())))
                    .andExpect(status().isUnauthorized());
            mvc.perform(auth(get("/internal/bookings/" + bookingId), guest).param("userId", String.valueOf(guest.id())))
                    .andExpect(status().isUnauthorized());
            mvc.perform(internal(get("/internal/bookings/" + bookingId)).param("userId", String.valueOf(guest.id())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hotelId").value(hotelId))
                    .andExpect(jsonPath("$.status").value("RESERVED"))
                    .andExpect(jsonPath("$.roomType").isNotEmpty());
            mvc.perform(internal(get("/internal/bookings/" + bookingId)).param("userId", String.valueOf(manager.id())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("Booking " + bookingId + " not found"));
            mvc.perform(internal(get("/internal/bookings/confirmed")).param("userId", String.valueOf(guest.id()))
                            .param("hotelId", String.valueOf(hotelId)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0))); // only CONFIRMED bookings
        }
    }
}
