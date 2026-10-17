package com.HotelManagement.AirBnb.client;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.List;

/** booking-service's internal API: the facts that decide whether a stay can be reviewed. */
@Component
public class BookingClient {

    private static final Logger log = LoggerFactory.getLogger(BookingClient.class);

    /** status: RESERVED, ACCEPTED, CONFIRMED, CANCELLED or EXPIRED; paymentStatus may be null (no payment yet). */
    public record StayView(Long bookingId, Long userId, Long hotelId, String roomType, LocalDate checkInDate,
                           LocalDate checkOutDate, String status, String paymentStatus) {
    }

    private static final String SERVICE = "booking-service";

    private final RestClient client;

    public BookingClient(@Qualifier("bookingRestClient") RestClient client) {
        this.client = client;
    }

    /** The user's own booking; 404 "Booking {id} not found" when it isn't theirs or doesn't exist. */
    public StayView requireUsersBooking(Long bookingId, Long userId) {
        log.debug("Loading booking {} of user {} from booking-service", bookingId, userId);
        try {
            return client.get().uri(u -> u.path("/internal/bookings/{id}").queryParam("userId", userId).build(bookingId))
                    .retrieve().body(StayView.class);
        } catch (RestClientException e) {
            throw DownstreamErrors.translate(SERVICE, "booking lookup", e);
        }
    }

    /** The user's CONFIRMED bookings at the hotel, latest check-out first. */
    public List<StayView> confirmedStays(Long userId, Long hotelId) {
        try {
            List<StayView> stays = client.get()
                    .uri(u -> u.path("/internal/bookings/confirmed").queryParam("userId", userId)
                            .queryParam("hotelId", hotelId).build())
                    .retrieve().body(new ParameterizedTypeReference<>() {
                    });
            return stays == null ? List.of() : stays;
        } catch (RestClientException e) {
            throw DownstreamErrors.translate(SERVICE, "confirmed stays", e);
        }
    }
}
