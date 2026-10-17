package com.HotelManagement.AirBnb.guest.controller;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.HotelManagement.AirBnb.shared.security.CurrentUserService;
import com.HotelManagement.AirBnb.guest.dto.GuestRequest;
import com.HotelManagement.AirBnb.guest.dto.GuestResponse;
import com.HotelManagement.AirBnb.guest.service.GuestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The current user's saved guests (travelling party), reused across bookings. */
@RestController
@RequestMapping("/api/v1/guests")
public class GuestController {

    private static final Logger log = LoggerFactory.getLogger(GuestController.class);

    private final GuestService guestService;
    private final CurrentUserService currentUser;

    public GuestController(GuestService guestService, CurrentUserService currentUser) {
        this.guestService = guestService;
        this.currentUser = currentUser;
    }

    /**
     * <b>GET /api/v1/guests</b> — the caller's saved guests.
     * <p>
     * People the caller travels with ({@code id, name, gender, age}), oldest first. Shown as checkboxes
     * when booking.
     * <p>
     * Access: GUEST. 200 OK.
     *
     * @return the caller's guests
     */
    @GetMapping
    public List<GuestResponse> list(@AuthenticationPrincipal Jwt jwt) {
        log.debug("GET /api/v1/guests");
        return guestService.list(currentUser.require(jwt));
    }

    /**
     * <b>POST /api/v1/guests</b> — save a new guest.
     * <p>
     * Body: {@code {"name", "gender" (MALE|FEMALE|OTHER), "age" (0–120)}}. The guest belongs to the caller
     * and can be added to any of their bookings.
     * <p>
     * Access: GUEST.<br>
     * 201 Created: the saved guest. 400: missing or invalid fields (with {@code fieldErrors}).
     *
     * @param request the guest's details
     * @return the saved guest
     */
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public GuestResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GuestRequest request) {
        log.debug("POST /api/v1/guests");
        return guestService.create(currentUser.require(jwt), request);
    }

    /**
     * <b>PUT /api/v1/guests/{guestId}</b> — edit a saved guest.
     * <p>
     * Replaces name, gender and age. Bookings that include this guest show the new details.
     * <p>
     * Access: GUEST, owner only.<br>
     * 200 OK. 400: invalid fields. 404: not found or belongs to someone else.
     *
     * @param guestId guest id
     * @param request the new details
     * @return the updated guest
     */
    @PutMapping("/{guestId}")
    public GuestResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long guestId,
                                @Valid @RequestBody GuestRequest request) {
        log.debug("PUT /api/v1/guests/{guestId} guestId={}", guestId);
        return guestService.update(currentUser.require(jwt), guestId, request);
    }
}
