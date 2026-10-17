package com.HotelManagement.AirBnb.auth.controller;

import com.HotelManagement.AirBnb.auth.dto.AuthResponse;
import com.HotelManagement.AirBnb.auth.dto.LoginRequest;
import com.HotelManagement.AirBnb.auth.dto.SignupRequest;
import com.HotelManagement.AirBnb.auth.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account registration and login (public endpoints, no token needed).
 * Both return a signed JWT that the client sends as {@code Authorization: Bearer <token>} on every
 * protected API; the token carries the user id and roles (GUEST or HOTEL_MANAGER).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * <b>POST /api/auth/signup</b> — create a new account and log it in.
     * <p>
     * Registers a user with the chosen role (GUEST to book stays, HOTEL_MANAGER to list hotels). The
     * password is stored as a BCrypt hash, never in plain text.
     * <p>
     * Access: public.<br>
     * Body: {@code {"name", "email", "password", "role"}}.<br>
     * 201 Created: the new user's id, name, email, roles and a JWT {@code token}.<br>
     * 409 Conflict: the email is already registered. 400: malformed body or unknown role.
     *
     * @param request the new account's details
     * @return the created account and its access token
     */
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/signup")
    public AuthResponse signup(@RequestBody SignupRequest request) {
        log.debug("POST /api/auth/signup role={}", request.getRole());
        return authService.signup(request);
    }

    /**
     * <b>POST /api/auth/login</b> — log in with email and password.
     * <p>
     * Checks the password against the stored BCrypt hash and issues a fresh JWT for the account.
     * <p>
     * Access: public.<br>
     * Body: {@code {"email", "password"}}.<br>
     * 200 OK: the user's id, name, email, roles and a JWT {@code token}.<br>
     * 401 Unauthorized: wrong email or password (the message doesn't say which).
     *
     * @param request the login credentials
     * @return the account and its access token
     */
    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        log.debug("POST /api/auth/login");
        return authService.login(request);
    }
}
