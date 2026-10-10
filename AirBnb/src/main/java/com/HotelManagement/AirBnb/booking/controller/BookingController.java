package com.HotelManagement.AirBnb.booking.controller;

import com.HotelManagement.AirBnb.auth.service.CurrentUserService;
import com.HotelManagement.AirBnb.booking.dto.CreateBookingRequest;
import com.HotelManagement.AirBnb.booking.dto.GuestBookingResponse;
import com.HotelManagement.AirBnb.booking.dto.UpdateGuestsRequest;
import com.HotelManagement.AirBnb.booking.service.BookingService;
import com.HotelManagement.AirBnb.booking.service.GuestBookingService;
import com.HotelManagement.AirBnb.shared.dto.PageResponse;
import com.HotelManagement.AirBnb.shared.web.PhotoResponses;
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

    /**
     * <b>POST /api/v1/bookings</b> — reserve rooms (step 1 of booking).
     * <p>
     * Body: {@code {"hotelId", "roomId", "checkInDate", "checkOutDate", "roomsCount", "guestIds"?}}.
     * Re-checks availability under row locks, takes the rooms from inventory for every night and creates
     * a RESERVED booking held for {@code app.booking.hold-minutes} (default 15) while the guest pays.
     * The amount is computed by the server from each night's price x rooms; any amount sent by the client
     * is ignored. {@code guestIds} must be the caller's saved guests and fit the rooms' capacity; they can
     * also be added later but are required before paying.
     * <p>
     * Access: GUEST.<br>
     * 201 Created: the booking with reference, amount, holdExpiresAt and allowed actions.<br>
     * 400: invalid dates (past, check-out not after check-in, over 30 nights), rooms or guests.
     * 404: hotel or room type not found. 409: hotel inactive, dates closed or not enough rooms.
     *
     * @param request what to book
     * @return the reserved booking
     */
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public GuestBookingResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateBookingRequest request) {
        return bookingService.create(currentUser.require(jwt), request);
    }

    /**
     * <b>GET /api/v1/bookings</b> — "My Bookings": the caller's bookings.
     * <p>
     * {@code scope}: {@code upcoming} (confirmed, stay not over), {@code pending} (reserved/accepted,
     * awaiting payment), {@code past} (confirmed, stay over), {@code cancelled} (cancelled or expired) or
     * {@code all} (default). Upcoming/pending are sorted by check-in date, the others newest first. Each
     * item has hotel, cover photo URL, room type, dates, rooms, guest count, amount, statuses and which
     * actions (pay, cancel, edit guests) are currently allowed.
     * <p>
     * Access: GUEST; only the caller's own bookings.<br>
     * 200 OK. 400: unknown scope.
     *
     * @param scope which bookings to show
     * @param page zero-based page (default 0)
     * @param size page size, 1–100 (default 10)
     * @return one page of the caller's bookings
     */
    @GetMapping
    public PageResponse<GuestBookingResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestParam(required = false) String scope,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        return guestBookingService.list(currentUser.require(jwt), GuestBookingService.parseScope(scope),
                Math.max(page, 0), PhotoResponses.pageSize(size));
    }

    /**
     * <b>GET /api/v1/bookings/{bookingId}</b> — one of the caller's bookings.
     * <p>
     * Full details including the travelling guests, amount, booking and payment status, payment method,
     * hold expiry and allowed actions. Also used as the confirmation page after payment.
     * <p>
     * Access: GUEST, owner only.<br>
     * 200 OK. 404: not found or belongs to someone else.
     *
     * @param bookingId booking id
     * @return the booking
     */
    @GetMapping("/{bookingId}")
    public GuestBookingResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId) {
        return guestBookingService.get(currentUser.require(jwt), bookingId);
    }

    /**
     * <b>PATCH /api/v1/bookings/{bookingId}/guests</b> — set who is travelling.
     * <p>
     * Body: {@code {"guestIds": [...]}} — replaces the booking's guest list with these saved guests
     * (the diagram's PATCH /bookings with an array of guest ids). Allowed on active bookings before
     * check-in; the guests must belong to the caller and fit rooms x capacity.
     * <p>
     * Access: GUEST, owner only.<br>
     * 200 OK: the updated booking. 400: unknown guests or too many. 404: not found.
     * 409: booking not active or check-in reached.
     *
     * @param bookingId booking id
     * @param request the guest ids
     * @return the updated booking
     */
    @PatchMapping("/{bookingId}/guests")
    public GuestBookingResponse updateGuests(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId,
                                             @Valid @RequestBody UpdateGuestsRequest request) {
        return guestBookingService.updateGuests(currentUser.require(jwt), bookingId, request.guestIds());
    }

    /**
     * <b>PATCH /api/v1/bookings/{bookingId}/cancel</b> — guest cancels a booking.
     * <p>
     * Allowed for reserved, accepted or confirmed bookings until the day before check-in. Releases the
     * rooms back to inventory exactly once. If it was paid online, a full refund is requested from
     * Razorpay first (payment becomes REFUND_PENDING, then REFUNDED); unpaid and pay-at-hotel bookings are
     * simply cancelled. No cancellation fees.
     * <p>
     * Access: GUEST, owner only.<br>
     * 200 OK: the cancelled booking. 404: not found. 409: already cancelled/expired, too close to
     * check-in, or payment was collected at the hotel. 502: Razorpay couldn't start the refund (nothing
     * was changed).
     *
     * @param bookingId booking id
     * @return the cancelled booking
     */
    @PatchMapping("/{bookingId}/cancel")
    public GuestBookingResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId) {
        return guestBookingService.cancel(currentUser.require(jwt), bookingId);
    }
}
