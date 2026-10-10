package com.HotelManagement.AirBnb.controller;

import com.HotelManagement.AirBnb.dto.PageResponse;
import com.HotelManagement.AirBnb.dto.booking.BookingDetailResponse;
import com.HotelManagement.AirBnb.dto.booking.BookingResponse;
import com.HotelManagement.AirBnb.entity.enums.BookingStatus;
import com.HotelManagement.AirBnb.service.CurrentUserService;
import com.HotelManagement.AirBnb.service.ManagerBookingService;
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

@RestController
@RequestMapping("/api/v1/admin/bookings")
public class BookingAdminController {

    private final ManagerBookingService bookingService;
    private final CurrentUserService currentUser;

    public BookingAdminController(ManagerBookingService bookingService, CurrentUserService currentUser) {
        this.bookingService = bookingService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<BookingResponse> list(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(required = false) Long hotelId,
                                              @RequestParam(required = false) BookingStatus status,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                              @RequestParam(required = false) String reference,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
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
            throw new com.HotelManagement.AirBnb.exception.BadRequestException("Booking reference looks like BK000123");
        }
    }

    @GetMapping("/{bookingId}")
    public BookingDetailResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId) {
        return bookingService.get(currentUser.require(jwt).getId(), bookingId);
    }

    @PatchMapping("/{bookingId}/accept")
    public BookingDetailResponse accept(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId) {
        return bookingService.accept(currentUser.require(jwt).getId(), bookingId);
    }

    /** The guest paid a pay-at-hotel booking at the front desk. */
    @PatchMapping("/{bookingId}/payment-collected")
    public BookingDetailResponse paymentCollected(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId) {
        return bookingService.markPaymentCollected(currentUser.require(jwt).getId(), bookingId);
    }

    @PatchMapping("/{bookingId}/cancel")
    public BookingDetailResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId) {
        return bookingService.cancel(currentUser.require(jwt).getId(), bookingId);
    }
}
