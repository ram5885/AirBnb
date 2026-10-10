package com.HotelManagement.AirBnb.booking.service;

import com.HotelManagement.AirBnb.booking.dto.GuestBookingResponse;
import com.HotelManagement.AirBnb.booking.entity.Booking;
import com.HotelManagement.AirBnb.booking.entity.BookingStatus;
import com.HotelManagement.AirBnb.guest.dto.GuestResponse;
import com.HotelManagement.AirBnb.hotel.dto.PhotoMetadata;
import com.HotelManagement.AirBnb.hotel.entity.Hotel;
import com.HotelManagement.AirBnb.hotel.repository.HotelPhotoRepository;
import com.HotelManagement.AirBnb.payment.entity.Payment;
import com.HotelManagement.AirBnb.payment.entity.PaymentMethod;
import com.HotelManagement.AirBnb.payment.entity.PaymentStatus;
import com.HotelManagement.AirBnb.search.service.HotelSearchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Builds the guest's view of a booking, including which actions the server will allow. */
@Component
public class GuestBookingMapper {

    private static final Set<PaymentStatus> PAID = Set.of(PaymentStatus.CONFIRMED, PaymentStatus.REFUND_PENDING,
            PaymentStatus.REFUNDED);

    private final HotelPhotoRepository hotelPhotoRepository;
    private final long holdMinutes;
    private final String currency;

    public GuestBookingMapper(HotelPhotoRepository hotelPhotoRepository,
                              @Value("${app.booking.hold-minutes:15}") long holdMinutes,
                              @Value("${razorpay.currency:INR}") String currency) {
        this.hotelPhotoRepository = hotelPhotoRepository;
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

    public boolean canPay(Booking b) {
        boolean awaitingPayment = (b.getBookingStatus() == BookingStatus.RESERVED && !holdExpired(b))
                || b.getBookingStatus() == BookingStatus.ACCEPTED;
        return awaitingPayment && !isPaid(b) && !b.getCheckInDate().isBefore(LocalDate.now())
                && b.getHotel().getDeletedAt() == null;
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

    /** Public cover photo per hotel (only for hotels still publicly visible). */
    public Map<Long, String> coverPhotos(Collection<Hotel> hotels) {
        List<Long> visible = hotels.stream()
                .filter(h -> Boolean.TRUE.equals(h.getActive()) && h.getDeletedAt() == null)
                .map(Hotel::getHotelId).distinct().toList();
        if (visible.isEmpty()) {
            return Map.of();
        }
        return hotelPhotoRepository.findMetadataByHotelIds(visible).stream()
                .collect(Collectors.toMap(PhotoMetadata::ownerId,
                        p -> HotelSearchService.hotelPhotoUrl(p.ownerId(), p.id()), (a, b) -> a));
    }

    public GuestBookingResponse toResponse(Booking b, String coverPhotoUrl, long guestCount, boolean includeGuests) {
        List<GuestResponse> guests = includeGuests && b.getGuests() != null
                ? b.getGuests().stream().sorted(Comparator.comparing(g -> g.getId())).map(GuestResponse::from).toList()
                : null;
        Hotel h = b.getHotel();
        boolean active = BookingService.INVENTORY_HOLDING_STATUSES.contains(b.getBookingStatus());
        boolean beforeCheckIn = b.getCheckInDate().isAfter(LocalDate.now());
        return new GuestBookingResponse(b.getBookingId(), reference(b.getBookingId()), h.getHotelId(), h.getName(),
                h.getLocation().getCity(), h.getLocation().getState(), coverPhotoUrl, b.getRoom().getId(),
                b.getRoom().getType(), b.getCheckInDate(), b.getCheckOutDate(),
                ChronoUnit.DAYS.between(b.getCheckInDate(), b.getCheckOutDate()), b.getRoomsCount(), guestCount,
                b.getRoom().getCapacity() * b.getRoomsCount(), guests,
                b.getAmount(), currency, b.getBookingStatus(),
                b.getPayment() == null ? null : b.getPayment().getPaymentStatus(), methodOf(b.getPayment()),
                holdExpiresAt(b), canPay(b), guestCanCancel(b), active && beforeCheckIn,
                b.getCreatedAt(), b.getUpdatedAt());
    }

    public GuestBookingResponse toDetail(Booking b) {
        String cover = coverPhotos(List.of(b.getHotel())).get(b.getHotel().getHotelId());
        return toResponse(b, cover, b.getGuests() == null ? 0 : b.getGuests().size(), true);
    }
}
