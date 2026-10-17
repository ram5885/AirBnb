package com.HotelManagement.AirBnb.internal;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.HotelManagement.AirBnb.booking.entity.Booking;
import com.HotelManagement.AirBnb.booking.repository.BookingRepository;
import com.HotelManagement.AirBnb.booking.service.BookingViews;
import com.HotelManagement.AirBnb.shared.exception.ResourceNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;

/**
 * Service-to-service API for review-service (X-Internal-Token required, never routed by the gateway). Lookups are
 * always scoped to the user: someone else's booking id looks like a missing one.
 */
@RestController
@RequestMapping("/internal/bookings")
@Transactional(readOnly = true)
public class InternalBookingController {

    private static final Logger log = LoggerFactory.getLogger(InternalBookingController.class);

    private final BookingRepository bookingRepository;
    private final BookingViews views;

    public InternalBookingController(BookingRepository bookingRepository, BookingViews views) {
        this.bookingRepository = bookingRepository;
        this.views = views;
    }

    /** The user's booking, or 404 "Booking {id} not found". */
    @GetMapping("/{bookingId}")
    public StayView get(@PathVariable Long bookingId, @RequestParam Long userId) {
        log.debug("GET /internal/bookings/{bookingId} bookingId={} userId={}", bookingId, userId);
        Booking b = bookingRepository.findForUser(bookingId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + bookingId + " not found"));
        return toViews(List.of(b)).getFirst();
    }

    /** The user's CONFIRMED bookings at the hotel, latest check-out first (candidates for a review). */
    @GetMapping("/confirmed")
    public List<StayView> confirmed(@RequestParam Long userId, @RequestParam Long hotelId) {
        log.debug("GET /internal/bookings/confirmed userId={} hotelId={}", userId, hotelId);
        return toViews(bookingRepository.findConfirmedForUserAtHotel(userId, hotelId));
    }

    private List<StayView> toViews(Collection<Booking> bookings) {
        BookingViews.Catalog catalog = views.forBookings(bookings);
        return bookings.stream().map(b -> new StayView(b.getBookingId(), b.getUserId(), b.getHotelId(),
                catalog.room(b).type(), b.getCheckInDate(), b.getCheckOutDate(), b.getBookingStatus(),
                b.getPayment() == null ? null : b.getPayment().getPaymentStatus())).toList();
    }
}
