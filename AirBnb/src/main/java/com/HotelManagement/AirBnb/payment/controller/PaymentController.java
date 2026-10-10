package com.HotelManagement.AirBnb.payment.controller;

import com.HotelManagement.AirBnb.auth.service.CurrentUserService;
import com.HotelManagement.AirBnb.booking.dto.GuestBookingResponse;
import com.HotelManagement.AirBnb.payment.dto.PaymentFailureRequest;
import com.HotelManagement.AirBnb.payment.dto.PaymentOrderResponse;
import com.HotelManagement.AirBnb.payment.dto.PaymentResultResponse;
import com.HotelManagement.AirBnb.payment.dto.VerifyPaymentRequest;
import com.HotelManagement.AirBnb.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Razorpay Checkout integration: create order, verify payment, record failures, receive webhooks. */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final CurrentUserService currentUser;

    public PaymentController(PaymentService paymentService, CurrentUserService currentUser) {
        this.paymentService = paymentService;
        this.currentUser = currentUser;
    }

    /** Creates (or reuses) the Razorpay order for a booking and returns what Checkout needs. */
    @PostMapping("/{bookingId}")
    public PaymentOrderResponse createOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId) {
        return paymentService.createOrder(currentUser.require(jwt), bookingId);
    }

    /** Checkout success handler payload; the booking is confirmed only after server-side verification. */
    @PostMapping("/verify")
    public ResponseEntity<PaymentResultResponse> verify(@AuthenticationPrincipal Jwt jwt,
                                                        @Valid @RequestBody VerifyPaymentRequest request) {
        PaymentService.VerifyOutcome outcome = paymentService.verify(currentUser.require(jwt), request);
        return ResponseEntity.status(outcome.status()).body(outcome.body());
    }

    /** Confirm the booking now and pay at the hotel (no online payment). */
    @PostMapping("/{bookingId}/pay-at-hotel")
    public GuestBookingResponse payAtHotel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId) {
        return paymentService.payAtHotel(currentUser.require(jwt), bookingId);
    }

    @PostMapping("/{bookingId}/failure")
    public GuestBookingResponse failure(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId,
                                        @Valid @RequestBody PaymentFailureRequest request) {
        return paymentService.recordFailure(currentUser.require(jwt), bookingId, request);
    }

    /** Razorpay webhook. The raw body is needed for the HMAC signature check. */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> webhook(@RequestBody String rawBody,
                                                       @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
                                                       @RequestHeader(value = "x-razorpay-event-id", required = false) String eventId) {
        try {
            boolean processed = paymentService.handleWebhook(rawBody, signature, eventId);
            return ResponseEntity.ok(Map.of("status", processed ? "processed" : "duplicate"));
        } catch (DataIntegrityViolationException e) {
            // The same event delivered concurrently: the other delivery recorded it.
            return ResponseEntity.ok(Map.of("status", "duplicate"));
        }
    }
}
