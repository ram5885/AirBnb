package com.HotelManagement.AirBnb.booking.controller;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.HotelManagement.AirBnb.shared.security.CurrentUserService;
import com.HotelManagement.AirBnb.booking.dto.BookingDetailResponse;
import com.HotelManagement.AirBnb.booking.dto.BookingResponse;
import com.HotelManagement.AirBnb.booking.entity.BookingStatus;
import com.HotelManagement.AirBnb.booking.service.ManagerBookingService;
import com.HotelManagement.AirBnb.shared.dto.PageResponse;
import com.HotelManagement.AirBnb.shared.exception.BadRequestException;
import com.HotelManagement.AirBnb.shared.web.PhotoResponses;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Booking management for hotel managers (role HOTEL_MANAGER, enforced in SecurityConfig).
 * Every endpoint is scoped to hotels owned by the caller (taken from the JWT): another manager's
 * booking is reported as 404, exactly like a booking that doesn't exist.
 */
@RestController
@RequestMapping("/api/v1/admin/bookings")
public class BookingAdminController {

    private static final Logger log = LoggerFactory.getLogger(BookingAdminController.class);

    private final ManagerBookingService bookingService;
    private final CurrentUserService currentUser;

    public BookingAdminController(ManagerBookingService bookingService, CurrentUserService currentUser) {
        this.bookingService = bookingService;
        this.currentUser = currentUser;
    }

    /**
     * <b>GET /api/v1/admin/bookings</b> — bookings at the caller's hotels, newest first.
     * <p>
     * Optional filters: {@code hotelId}; {@code status} (RESERVED, ACCEPTED, CONFIRMED, CANCELLED,
     * EXPIRED); {@code startDate}/{@code endDate} (stays overlapping that window); {@code reference}
     * ("BK000123" or the plain id). Each row has hotel, room type, dates, nights, rooms, guest count,
     * amount, booking status, payment status and method, and the booker's name.
     * <p>
     * Access: HOTEL_MANAGER.<br>
     * 200 OK. 400: invalid dates or reference format.
     *
     * @param hotelId optional hotel filter
     * @param status optional status filter
     * @param startDate optional start of the stay window
     * @param endDate optional end of the stay window
     * @param reference optional booking reference
     * @param page zero-based page (default 0)
     * @param size page size, 1–100 (default 20)
     * @return one page of bookings
     */
    @GetMapping
    public PageResponse<BookingResponse> list(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(required = false) Long hotelId,
                                              @RequestParam(required = false) BookingStatus status,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                              @RequestParam(required = false) String reference,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        log.debug("GET /api/v1/admin/bookings hotelId={} status={} startDate={} endDate={} reference={} page={} size={}", hotelId, status, startDate, endDate, reference, page, size);
        return bookingService.list(currentUser.require(jwt).getId(), hotelId, status, startDate, endDate,
                parseReference(reference), Math.max(page, 0), PhotoResponses.pageSize(size));
    }

    /** Booking reference as shown to users ("BK000123") or the plain booking id ("123"). */
    static Long parseReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        String digits = reference.strip().toUpperCase().replaceFirst("^BK", "");
        try {
            return Long.valueOf(digits);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Booking reference looks like BK000123");
        }
    }

    /**
     * <b>GET /api/v1/admin/bookings/{bookingId}</b> — booking details for the hotel manager.
     * <p>
     * Includes the booker's name and email and the travelling guests (name, gender, age), which are only
     * shown here, plus amount, payment status/method and whether the hotel has since been deleted.
     * <p>
     * Access: HOTEL_MANAGER, owner of the booking's hotel.<br>
     * 200 OK. 404: not found or another manager's booking.
     *
     * @param bookingId booking id
     * @return the booking
     */
    @GetMapping("/{bookingId}")
    public BookingDetailResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId) {
        log.debug("GET /api/v1/admin/bookings/{bookingId} bookingId={}", bookingId);
        return bookingService.get(currentUser.require(jwt).getId(), bookingId);
    }

    /**
     * <b>PATCH /api/v1/admin/bookings/{bookingId}/accept</b> — accept a reserved booking.
     * <p>
     * Moves a RESERVED booking to ACCEPTED (its rooms stay held and it no longer expires). The guest still
     * has to pay to confirm it. Only valid before check-in and while the hotel and room type exist.
     * <p>
     * Access: HOTEL_MANAGER, owner of the booking's hotel.<br>
     * 200 OK: the updated booking. 404: not found. 409: not RESERVED, check-in passed, or hotel/room
     * type deleted.
     *
     * @param bookingId booking id
     * @return the accepted booking
     */
    @PatchMapping("/{bookingId}/accept")
    public BookingDetailResponse accept(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId) {
        log.debug("PATCH /api/v1/admin/bookings/{bookingId}/accept bookingId={}", bookingId);
        return bookingService.accept(currentUser.require(jwt).getId(), bookingId);
    }

    /**
     * <b>PATCH /api/v1/admin/bookings/{bookingId}/payment-collected</b> — record a pay-at-hotel payment.
     * <p>
     * Marks the payment of a confirmed pay-at-hotel booking as collected at the front desk (payment status
     * CONFIRMED). After this the booking can no longer be cancelled online; any cancellation or refund is
     * handled at the hotel.
     * <p>
     * Access: HOTEL_MANAGER, owner of the booking's hotel.<br>
     * 200 OK: the updated booking. 404: not found. 409: not a pay-at-hotel booking, not confirmed, or
     * already collected.
     *
     * @param bookingId booking id
     * @return the updated booking
     */
    @PatchMapping("/{bookingId}/payment-collected")
    public BookingDetailResponse paymentCollected(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId) {
        log.debug("PATCH /api/v1/admin/bookings/{bookingId}/payment-collected bookingId={}", bookingId);
        return bookingService.markPaymentCollected(currentUser.require(jwt).getId(), bookingId);
    }

    /**
     * <b>PATCH /api/v1/admin/bookings/{bookingId}/cancel</b> — hotel manager cancels a booking.
     * <p>
     * Allowed for reserved, accepted or confirmed bookings until the check-out date. Releases the rooms
     * exactly once; if the guest paid online, a full refund is requested from Razorpay first. No fees.
     * <p>
     * Access: HOTEL_MANAGER, owner of the booking's hotel.<br>
     * 200 OK: the cancelled booking. 404: not found. 409: already cancelled/expired, stay completed, or
     * payment collected at the hotel. 502: Razorpay couldn't start the refund (nothing was changed).
     *
     * @param bookingId booking id
     * @return the cancelled booking
     */
    @PatchMapping("/{bookingId}/cancel")
    public BookingDetailResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId) {
        log.debug("PATCH /api/v1/admin/bookings/{bookingId}/cancel bookingId={}", bookingId);
        return bookingService.cancel(currentUser.require(jwt).getId(), bookingId);
    }
}
