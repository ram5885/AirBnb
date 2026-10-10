package com.HotelManagement.AirBnb.controller;

import com.HotelManagement.AirBnb.dto.PageResponse;
import com.HotelManagement.AirBnb.dto.booking.CreateBookingRequest;
import com.HotelManagement.AirBnb.dto.booking.GuestBookingResponse;
import com.HotelManagement.AirBnb.dto.booking.UpdateGuestsRequest;
import com.HotelManagement.AirBnb.service.BookingService;
import com.HotelManagement.AirBnb.service.CurrentUserService;
import com.HotelManagement.AirBnb.service.GuestBookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Guest booking API ("My Bookings"). The owner is always the authenticated user from the JWT. */
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final GuestBookingService guestBookingService;
    private final CurrentUserService currentUser;

    public BookingController(BookingService bookingService, GuestBookingService guestBookingService,
                             CurrentUserService currentUser) {
        this.bookingService = bookingService;
        this.guestBookingService = guestBookingService;
        this.currentUser = currentUser;
    }

    /** Reserves the rooms (RESERVED, held for app.booking.hold-minutes) at a server-computed price. */
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public GuestBookingResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateBookingRequest request) {
        return bookingService.create(currentUser.require(jwt), request);
    }

    // scope: all (default), upcoming, pending, past, cancelled
    @GetMapping
    public PageResponse<GuestBookingResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestParam(required = false) String scope,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        return guestBookingService.list(currentUser.require(jwt), GuestBookingService.parseScope(scope),
                Math.max(page, 0), PhotoResponses.pageSize(size));
    }

    @GetMapping("/{bookingId}")
    public GuestBookingResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId) {
        return guestBookingService.get(currentUser.require(jwt), bookingId);
    }

    /** Replace the travelling guests (the diagram's PATCH /bookings with an array of guest ids). */
    @PatchMapping("/{bookingId}/guests")
    public GuestBookingResponse updateGuests(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId,
                                             @Valid @RequestBody UpdateGuestsRequest request) {
        return guestBookingService.updateGuests(currentUser.require(jwt), bookingId, request.guestIds());
    }

    /** Cancel until the day before check-in; paid bookings are refunded in full. */
    @PatchMapping("/{bookingId}/cancel")
    public GuestBookingResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId) {
        return guestBookingService.cancel(currentUser.require(jwt), bookingId);
    }
}
