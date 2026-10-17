package com.HotelManagement.AirBnb.payment.controller;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.HotelManagement.AirBnb.shared.security.CurrentUserService;
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

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;
    private final CurrentUserService currentUser;

    public PaymentController(PaymentService paymentService, CurrentUserService currentUser) {
        this.paymentService = paymentService;
        this.currentUser = currentUser;
    }

    /**
     * <b>POST /api/v1/payments/{bookingId}</b> — start (or resume) online payment for a booking.
     * <p>
     * Creates a Razorpay order for the booking's server-computed amount (in paise, INR) and returns what
     * Razorpay Checkout needs: public key id, order id, amount, currency, description and the guest's
     * name/email for prefill. Calling it again (retry or reopened checkout) reuses the same order, so
     * retries never create duplicate orders or bookings. The key secret is never returned.
     * <p>
     * Access: GUEST, owner of the booking.<br>
     * 200 OK. 400: no guests on the booking yet. 404: not found. 409: already paid, hold expired or booking
     * not payable. 503: Razorpay keys not configured. 502: Razorpay unavailable.
     *
     * @param bookingId booking id
     * @return the Checkout details
     */
    @PostMapping("/{bookingId}")
    public PaymentOrderResponse createOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId) {
        log.debug("POST /api/v1/payments/{bookingId} bookingId={}", bookingId);
        return paymentService.createOrder(currentUser.require(jwt), bookingId);
    }

    /**
     * <b>POST /api/v1/payments/verify</b> — confirm a payment after Razorpay Checkout succeeds.
     * <p>
     * Body: {@code {"bookingId", "razorpayOrderId", "razorpayPaymentId", "razorpaySignature"}} from the
     * Checkout success handler. The server checks the signature with the key secret, then fetches the
     * payment from Razorpay and checks order, amount, currency and status (capturing it if only
     * authorized). Only then is the booking CONFIRMED. Repeated calls for the same payment are harmless.
     * If the hold had expired, the rooms are re-taken when still free, otherwise the payment is refunded.
     * <p>
     * Access: GUEST, owner of the booking.<br>
     * 200 OK: {@code {"confirmed": true, "message", "booking"}}.<br>
     * 402: the payment failed (booking stays payable). 409: amount/order mismatch, not completed yet,
     * or refunded because the rooms were gone (body has the details). 400: invalid signature or the order
     * isn't this booking's. 404: not found. 502: Razorpay unavailable.
     *
     * @param request the Checkout response
     * @return the verification result with the updated booking
     */
    @PostMapping("/verify")
    public ResponseEntity<PaymentResultResponse> verify(@AuthenticationPrincipal Jwt jwt,
                                                        @Valid @RequestBody VerifyPaymentRequest request) {
        log.debug("POST /api/v1/payments/verify");
        PaymentService.VerifyOutcome outcome = paymentService.verify(currentUser.require(jwt), request);
        return ResponseEntity.status(outcome.status()).body(outcome.body());
    }

    /**
     * <b>POST /api/v1/payments/{bookingId}/pay-at-hotel</b> — confirm now, pay at the hotel.
     * <p>
     * Confirms a reserved/accepted booking without online payment: the booking becomes CONFIRMED (rooms
     * stay held and never expire) and the payment is PAY_AT_HOTEL / PENDING until the manager marks it
     * collected. Works even when Razorpay isn't configured.
     * <p>
     * Access: GUEST, owner of the booking.<br>
     * 200 OK: the confirmed booking. 400: no guests on the booking. 404: not found. 409: already paid or
     * confirmed, hold expired, or pay at hotel disabled.
     *
     * @param bookingId booking id
     * @return the confirmed booking
     */
    @PostMapping("/{bookingId}/pay-at-hotel")
    public GuestBookingResponse payAtHotel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId) {
        log.debug("POST /api/v1/payments/{bookingId}/pay-at-hotel bookingId={}", bookingId);
        return paymentService.payAtHotel(currentUser.require(jwt), bookingId);
    }

    /**
     * <b>POST /api/v1/payments/{bookingId}/failure</b> — record a failed payment attempt.
     * <p>
     * Body: {@code {"razorpayOrderId", "razorpayPaymentId"?, "code"?, "description"?}} from Checkout's
     * payment.failed event. Marks the online payment FAILED with the reason so the guest can retry while
     * the hold lasts. It can never confirm or cancel a booking, and it is ignored for pay-at-hotel
     * bookings.
     * <p>
     * Access: GUEST, owner of the booking.<br>
     * 200 OK: the booking. 400: the order isn't this booking's. 404: not found.
     *
     * @param bookingId booking id
     * @param request the failure details
     * @return the booking
     */
    @PostMapping("/{bookingId}/failure")
    public GuestBookingResponse failure(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId,
                                        @Valid @RequestBody PaymentFailureRequest request) {
        log.debug("POST /api/v1/payments/{bookingId}/failure bookingId={}", bookingId);
        return paymentService.recordFailure(currentUser.require(jwt), bookingId, request);
    }

    /**
     * <b>POST /api/v1/payments/webhook</b> — Razorpay server-to-server notifications.
     * <p>
     * Receives payment.captured / order.paid (confirm the booking), payment.failed (mark the attempt
     * failed) and refund.processed / refund.failed (update refund status). The request is trusted only if
     * its {@code X-Razorpay-Signature} matches an HMAC of the raw body with the webhook secret. Each
     * {@code x-razorpay-event-id} is processed once, so re-deliveries are safe. Ensures bookings are
     * confirmed even if the guest's browser never called /verify.
     * <p>
     * Access: public (authenticated by signature, not by token).<br>
     * 200 OK: {@code {"status": "processed" | "duplicate"}}. 400: invalid signature. 503: webhook secret
     * not configured.
     *
     * @param rawBody the exact request body (needed for the signature check)
     * @param signature the X-Razorpay-Signature header
     * @param eventId the x-razorpay-event-id header
     * @return whether the event was processed or a duplicate
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> webhook(@RequestBody String rawBody,
                                                       @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
                                                       @RequestHeader(value = "x-razorpay-event-id", required = false) String eventId) {
        log.debug("POST /api/v1/payments/webhook rawBody={}", rawBody);
        try {
            boolean processed = paymentService.handleWebhook(rawBody, signature, eventId);
            return ResponseEntity.ok(Map.of("status", processed ? "processed" : "duplicate"));
        } catch (DataIntegrityViolationException e) {
            // The same event delivered concurrently: the other delivery recorded it.
            return ResponseEntity.ok(Map.of("status", "duplicate"));
        }
    }
}
