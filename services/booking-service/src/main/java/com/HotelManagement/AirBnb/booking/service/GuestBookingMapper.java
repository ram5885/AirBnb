package com.HotelManagement.AirBnb.booking.service;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.HotelManagement.AirBnb.booking.dto.GuestBookingResponse;
import com.HotelManagement.AirBnb.booking.entity.Booking;
import com.HotelManagement.AirBnb.booking.entity.BookingStatus;
import com.HotelManagement.AirBnb.booking.service.BookingViews.Catalog;
import com.HotelManagement.AirBnb.booking.service.BookingViews.HotelFacts;
import com.HotelManagement.AirBnb.booking.service.BookingViews.RoomFacts;
import com.HotelManagement.AirBnb.guest.dto.GuestResponse;
import com.HotelManagement.AirBnb.payment.entity.Payment;
import com.HotelManagement.AirBnb.payment.entity.PaymentMethod;
import com.HotelManagement.AirBnb.payment.entity.PaymentStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Builds the guest's view of a booking, including which actions the server will allow. Hotel and room-type
 * facts come from catalog-service through {@link BookingViews} (falling back to the booking's snapshot).
 */
@Component
public class GuestBookingMapper {

    private static final Logger log = LoggerFactory.getLogger(GuestBookingMapper.class);

    private static final Set<PaymentStatus> PAID = Set.of(PaymentStatus.CONFIRMED, PaymentStatus.REFUND_PENDING,
            PaymentStatus.REFUNDED);

    private final BookingViews views;
    private final long holdMinutes;
    private final String currency;

    public GuestBookingMapper(BookingViews views,
                              @Value("${app.booking.hold-minutes:15}") long holdMinutes,
                              @Value("${razorpay.currency:INR}") String currency) {
        this.views = views;
        this.holdMinutes = holdMinutes;
        this.currency = currency;
    }

    public long holdMinutes() {
        return holdMinutes;
    }

    /** When an unpaid RESERVED booking stops holding its rooms. */
    public LocalDateTime holdExpiresAt(Booking b) {
        return b.getBookingStatus() == BookingStatus.RESERVED && b.getCreatedAt() != null
                ? b.getCreatedAt().plusMinutes(holdMinutes) : null;
    }

    public boolean holdExpired(Booking b) {
        LocalDateTime expires = holdExpiresAt(b);
        return expires != null && expires.isBefore(LocalDateTime.now());
    }

    public boolean canPay(Booking b, HotelFacts hotel) {
        boolean awaitingPayment = (b.getBookingStatus() == BookingStatus.RESERVED && !holdExpired(b))
                || b.getBookingStatus() == BookingStatus.ACCEPTED;
        return awaitingPayment && !isPaid(b) && !b.getCheckInDate().isBefore(LocalDate.now()) && !hotel.deleted();
    }

    /**
     * Guests may cancel an active booking until the day before check-in, unless the hotel has already
     * collected a pay-at-hotel payment (that has to be settled at the hotel).
     */
    public boolean guestCanCancel(Booking b) {
        return BookingService.INVENTORY_HOLDING_STATUSES.contains(b.getBookingStatus())
                && b.getCheckInDate().isAfter(LocalDate.now()) && !collectedAtHotel(b);
    }

    public static PaymentMethod methodOf(Payment p) {
        return p == null ? null : p.getMethod() == null ? PaymentMethod.ONLINE : p.getMethod();
    }

    /** Pay-at-hotel booking whose payment the hotel has marked as collected. */
    public static boolean collectedAtHotel(Booking b) {
        return methodOf(b.getPayment()) == PaymentMethod.PAY_AT_HOTEL
                && b.getPayment().getPaymentStatus() == PaymentStatus.CONFIRMED;
    }

    public boolean isPaid(Booking b) {
        return b.getPayment() != null && PAID.contains(b.getPayment().getPaymentStatus());
    }

    public static String reference(Long bookingId) {
        return "BK" + String.format("%06d", bookingId);
    }

    public GuestBookingResponse toResponse(Booking b, Catalog catalog, long guestCount, boolean includeGuests) {
        List<GuestResponse> guests = includeGuests && b.getGuests() != null
                ? b.getGuests().stream().sorted(Comparator.comparing(g -> g.getId())).map(GuestResponse::from).toList()
                : null;
        HotelFacts hotel = catalog.hotel(b);
        RoomFacts room = catalog.room(b);
        boolean active = BookingService.INVENTORY_HOLDING_STATUSES.contains(b.getBookingStatus());
        boolean beforeCheckIn = b.getCheckInDate().isAfter(LocalDate.now());
        int capacity = Objects.requireNonNullElse(room.capacity(), 0);
        return new GuestBookingResponse(b.getBookingId(), reference(b.getBookingId()), b.getHotelId(), hotel.name(),
                hotel.city(), hotel.state(), hotel.coverPhotoUrl(), b.getRoomId(),
                room.type(), b.getCheckInDate(), b.getCheckOutDate(),
                ChronoUnit.DAYS.between(b.getCheckInDate(), b.getCheckOutDate()), b.getRoomsCount(), guestCount,
                capacity * b.getRoomsCount(), guests,
                b.getAmount(), currency, b.getBookingStatus(),
                b.getPayment() == null ? null : b.getPayment().getPaymentStatus(), methodOf(b.getPayment()),
                holdExpiresAt(b), canPay(b, hotel), guestCanCancel(b), active && beforeCheckIn,
                b.getCreatedAt(), b.getUpdatedAt());
    }

    public GuestBookingResponse toDetail(Booking b) {
        log.trace("Mapping booking {} (status {}) for its guest", b.getBookingId(), b.getBookingStatus());
        return toResponse(b, views.forBookings(List.of(b)), b.getGuests() == null ? 0 : b.getGuests().size(), true);
    }
}
