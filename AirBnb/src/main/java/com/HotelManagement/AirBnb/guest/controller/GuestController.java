package com.HotelManagement.AirBnb.guest.controller;

import com.HotelManagement.AirBnb.auth.service.CurrentUserService;
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

    private final GuestService guestService;
    private final CurrentUserService currentUser;

    public GuestController(GuestService guestService, CurrentUserService currentUser) {
        this.guestService = guestService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<GuestResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return guestService.list(currentUser.require(jwt));
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public GuestResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GuestRequest request) {
        return guestService.create(currentUser.require(jwt), request);
    }

    @PutMapping("/{guestId}")
    public GuestResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long guestId,
                                @Valid @RequestBody GuestRequest request) {
        return guestService.update(currentUser.require(jwt), guestId, request);
    }
}
