package com.HotelManagement.AirBnb.payment.service;

import com.HotelManagement.AirBnb.shared.logging.AuditLogger;
import com.HotelManagement.AirBnb.booking.dto.GuestBookingResponse;
import com.HotelManagement.AirBnb.booking.entity.Booking;
import com.HotelManagement.AirBnb.booking.entity.BookingStatus;
import com.HotelManagement.AirBnb.booking.entity.HoldStatus;
import com.HotelManagement.AirBnb.booking.repository.BookingRepository;
import com.HotelManagement.AirBnb.booking.service.BookingCancellationService;
import com.HotelManagement.AirBnb.booking.service.BookingViews;
import com.HotelManagement.AirBnb.booking.service.GuestBookingMapper;
import com.HotelManagement.AirBnb.client.CatalogClient;
import com.HotelManagement.AirBnb.payment.dto.PaymentFailureRequest;
import com.HotelManagement.AirBnb.payment.dto.PaymentOrderResponse;
import com.HotelManagement.AirBnb.payment.dto.PaymentResultResponse;
import com.HotelManagement.AirBnb.payment.dto.VerifyPaymentRequest;
import com.HotelManagement.AirBnb.payment.entity.Payment;
import com.HotelManagement.AirBnb.payment.entity.PaymentMethod;
import com.HotelManagement.AirBnb.payment.entity.PaymentStatus;
import com.HotelManagement.AirBnb.payment.entity.PaymentWebhookEvent;
import com.HotelManagement.AirBnb.payment.gateway.PaymentGateway;
import com.HotelManagement.AirBnb.payment.gateway.PaymentGateway.GatewayPayment;
import com.HotelManagement.AirBnb.payment.gateway.RazorpaySignatureVerifier;
import com.HotelManagement.AirBnb.payment.repository.PaymentRepository;
import com.HotelManagement.AirBnb.payment.repository.PaymentWebhookEventRepository;
import com.HotelManagement.AirBnb.shared.exception.BadRequestException;
import com.HotelManagement.AirBnb.shared.exception.ConflictException;
import com.HotelManagement.AirBnb.shared.exception.ResourceNotFoundException;
import com.HotelManagement.AirBnb.shared.exception.ServiceUnavailableException;
import com.HotelManagement.AirBnb.shared.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Set;

/**
 * Razorpay Standard Checkout flow for a RESERVED/ACCEPTED booking:
 * <ol>
 *   <li>createOrder: server computes the amount from the booking and creates (or reuses) a gateway order.</li>
 *   <li>The browser pays through Razorpay Checkout.</li>
 *   <li>verify: the checkout signature is checked with the key secret, then the payment is fetched from
 *       Razorpay and its order, amount, currency and status are checked (authorized payments are captured).
 *       Only then is the booking CONFIRMED. The browser's word alone never confirms anything.</li>
 *   <li>Webhooks (payment.captured / order.paid / payment.failed / refund.*) apply the same rules
 *       asynchronously, idempotently, in case the browser never reports back.</li>
 * </ol>
 * Every state change happens under the booking's row lock, so retries, duplicate callbacks and
 * webhooks can't confirm, refund or release anything twice.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final Set<PaymentStatus> SETTLED = Set.of(PaymentStatus.CONFIRMED, PaymentStatus.REFUND_PENDING,
            PaymentStatus.REFUNDED);

    /** HTTP status plus body for a verification attempt (state changes are committed either way). */
    public record VerifyOutcome(HttpStatus status, PaymentResultResponse body) {
    }

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final PaymentGateway gateway;
    private final RazorpaySignatureVerifier signatureVerifier;
    private final CatalogClient catalogClient;
    private final BookingViews views;
    private final BookingCancellationService cancellationService;
    private final GuestBookingMapper mapper;
    private final JsonMapper jsonMapper;
    private final String currency;
    private final boolean payAtHotelEnabled;

    public PaymentService(BookingRepository bookingRepository, PaymentRepository paymentRepository,
                          PaymentWebhookEventRepository webhookEventRepository, PaymentGateway gateway,
                          RazorpaySignatureVerifier signatureVerifier, CatalogClient catalogClient, BookingViews views,
                          BookingCancellationService cancellationService, GuestBookingMapper mapper,
                          JsonMapper jsonMapper, @Value("${razorpay.currency:INR}") String currency,
                          @Value("${app.payments.pay-at-hotel-enabled:true}") boolean payAtHotelEnabled) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.gateway = gateway;
        this.signatureVerifier = signatureVerifier;
        this.catalogClient = catalogClient;
        this.views = views;
        this.cancellationService = cancellationService;
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
        this.currency = currency;
        this.payAtHotelEnabled = payAtHotelEnabled;
    }

    /** Amount in currency subunits (paise for INR). */
    public static long toSubunits(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    // ------------------------------------------------------------ 1. order

    /**
     * Creates the gateway order for the booking's server-computed amount, or returns the existing one
     * (so retrying after a failure or a closed checkout never creates duplicate orders or bookings).
     */
    @Transactional
    public PaymentOrderResponse createOrder(CurrentUser user, Long bookingId) {
        if (!gateway.isEnabled()) {
            log.warn("Payment order for booking {} refused: Razorpay is not configured", bookingId);
            throw new ServiceUnavailableException("Online payments aren't available right now. Please try again later.");
        }
        Booking booking = bookingRepository.findForUserForUpdate(bookingId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + bookingId + " not found"));
        if (mapper.isPaid(booking)) {
            throw new ConflictException("This booking has already been paid.");
        }
        BookingViews.Catalog catalog = views.live(booking); // whether the hotel still exists decides; 503 if unknown
        if (!mapper.canPay(booking, catalog.hotel(booking))) {
            throw new ConflictException(mapper.holdExpired(booking) || booking.getBookingStatus() == BookingStatus.EXPIRED
                    ? "Your reservation has expired. Please search and book again."
                    : "This booking can't be paid (status " + booking.getBookingStatus() + ").");
        }
        if (booking.getGuests() == null || booking.getGuests().isEmpty()) {
            throw new BadRequestException("Add at least one guest before paying.");
        }
        if (booking.getAmount() == null || booking.getAmount().signum() <= 0) {
            throw new ConflictException("This booking has no amount to pay.");
        }
        long amount = toSubunits(booking.getAmount());

        Payment payment = booking.getPayment();
        boolean reusable = payment != null && payment.getRazorpayOrderId() != null
                && (payment.getPaymentStatus() == PaymentStatus.PENDING || payment.getPaymentStatus() == PaymentStatus.FAILED)
                && toSubunits(payment.getAmount()) == amount && currency.equals(payment.getCurrency());
        if (!reusable) {
            PaymentGateway.GatewayOrder order = gateway.createOrder(amount, currency, receipt(booking), bookingId);
            if (order.amount() != amount || !currency.equalsIgnoreCase(order.currency())) {
                throw new ConflictException("The payment provider created an order with an unexpected amount.");
            }
            if (payment == null) {
                payment = new Payment();
                payment.setTransactionId(receipt(booking));
            }
            payment.setRazorpayOrderId(order.id());
            payment.setMethod(PaymentMethod.ONLINE);
            payment.setAmount(booking.getAmount());
            payment.setCurrency(currency);
            payment.setPaymentStatus(PaymentStatus.PENDING);
            payment.setFailureReason(null);
            booking.setPayment(paymentRepository.save(payment));
            log.info("Created Razorpay order {} for booking {} ({} paise)", order.id(), bookingId, amount);
            AuditLogger.success("PAYMENT_ORDER_CREATED", "bookingId", bookingId, "orderId", order.id(), "amountPaise", amount,
                    "currency", currency);
        } else if (payment.getPaymentStatus() == PaymentStatus.FAILED) {
            payment.setPaymentStatus(PaymentStatus.PENDING); // retry on the same order
            log.info("Reusing Razorpay order {} for a retry of booking {}", payment.getRazorpayOrderId(), bookingId);
        } else {
            log.debug("Reusing Razorpay order {} for booking {}", payment.getRazorpayOrderId(), bookingId);
        }

        return new PaymentOrderResponse(bookingId, gateway.keyId(), payment.getRazorpayOrderId(), amount, currency,
                booking.getAmount(), catalog.hotel(booking).name(),
                catalog.room(booking).type() + " · " + GuestBookingMapper.reference(bookingId),
                booking.getUserName(), booking.getUserEmail());
    }

    // ------------------------------------------------------------ pay at hotel

    /**
     * Confirms a RESERVED/ACCEPTED booking with payment due at the hotel: the booking becomes CONFIRMED
     * (so its rooms stay held and never expire) and the payment is PAY_AT_HOTEL / PENDING until the
     * hotel manager marks it collected. Doesn't need the payment gateway.
     */
    @Transactional
    public GuestBookingResponse payAtHotel(CurrentUser user, Long bookingId) {
        if (!payAtHotelEnabled) {
            throw new ConflictException("Pay at hotel isn't available.");
        }
        Booking booking = bookingRepository.findForUserForUpdate(bookingId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + bookingId + " not found"));
        if (mapper.isPaid(booking)) {
            throw new ConflictException("This booking has already been paid.");
        }
        if (!mapper.canPay(booking, views.live(booking).hotel(booking))) {
            throw new ConflictException(mapper.holdExpired(booking) || booking.getBookingStatus() == BookingStatus.EXPIRED
                    ? "Your reservation has expired. Please search and book again."
                    : "This booking can't be confirmed (status " + booking.getBookingStatus() + ").");
        }
        if (booking.getGuests() == null || booking.getGuests().isEmpty()) {
            throw new BadRequestException("Add at least one guest before confirming.");
        }
        Payment payment = booking.getPayment();
        if (payment == null) {
            payment = new Payment();
            payment.setTransactionId("pah_" + bookingId + "_" + System.currentTimeMillis());
        }
        // An online order started earlier is kept: if that payment still completes, it's recorded as paid online.
        payment.setMethod(PaymentMethod.PAY_AT_HOTEL);
        payment.setAmount(booking.getAmount());
        payment.setCurrency(currency);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setFailureReason(null);
        booking.setPayment(paymentRepository.save(payment));
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        bookingRepository.flush();
        log.info("Booking {} confirmed with payment at the hotel", bookingId);
        AuditLogger.success("PAY_AT_HOTEL_CHOSEN", "bookingId", bookingId, "amount", booking.getAmount());
        return mapper.toDetail(booking);
    }

    // ------------------------------------------------------------ 2. verify (checkout success handler)

    @Transactional
    public VerifyOutcome verify(CurrentUser user, VerifyPaymentRequest request) {
        Booking booking = bookingRepository.findForUserForUpdate(request.bookingId(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + request.bookingId() + " not found"));
        Payment payment = booking.getPayment();
        if (payment == null || payment.getRazorpayOrderId() == null
                || !payment.getRazorpayOrderId().equals(request.razorpayOrderId())) {
            throw new BadRequestException("This payment doesn't belong to this booking.");
        }
        if (!signatureVerifier.isPaymentSignatureValid(request.razorpayOrderId(), request.razorpayPaymentId(),
                request.razorpaySignature())) {
            log.warn("Rejected payment verification with an invalid signature for booking {}", booking.getBookingId());
            AuditLogger.failure("PAYMENT_VERIFIED", "bookingId", booking.getBookingId(), "orderId", request.razorpayOrderId(),
                    "paymentId", request.razorpayPaymentId(), "reason", "invalid signature");
            throw new BadRequestException("Payment verification failed. If money was deducted, it will be confirmed or refunded automatically.");
        }

        // Duplicate/replayed verification of an already-settled payment.
        if (SETTLED.contains(payment.getPaymentStatus())) {
            if (request.razorpayPaymentId().equals(payment.getRazorpayPaymentId())) {
                boolean confirmed = booking.getBookingStatus() == BookingStatus.CONFIRMED;
                return outcome(confirmed ? HttpStatus.OK : HttpStatus.CONFLICT, confirmed,
                        confirmed ? "Payment already confirmed." : "This payment was refunded.", booking);
            }
            throw new ConflictException("This booking has already been paid.");
        }

        GatewayPayment gp = gateway.fetchPayment(request.razorpayPaymentId());
        String problem = mismatch(gp, payment);
        if (problem != null) {
            log.error("Payment {} for booking {} rejected: {}", gp.id(), booking.getBookingId(), problem);
            AuditLogger.failure("PAYMENT_VERIFIED", "bookingId", booking.getBookingId(), "orderId", payment.getRazorpayOrderId(),
                    "paymentId", gp.id(), "reason", problem);
            if (onlineAttemptPending(payment)) {
                markFailed(payment, problem);
            }
            return outcome(HttpStatus.CONFLICT, false, "We couldn't verify this payment: " + problem + ".", booking);
        }
        if ("authorized".equals(gp.status())) {
            log.info("Capturing authorized payment {} for booking {}", gp.id(), booking.getBookingId());
            gp = gateway.capture(gp.id(), gp.amount(), gp.currency());
        }
        switch (gp.status() == null ? "" : gp.status()) {
            case "captured" -> {
                return applySuccessfulPayment(booking, payment, gp.id());
            }
            case "failed" -> {
                log.info("Payment {} for booking {} failed at Razorpay: {}", gp.id(), booking.getBookingId(), gp.errorDescription());
                AuditLogger.failure("PAYMENT_VERIFIED", "bookingId", booking.getBookingId(), "paymentId", gp.id(),
                        "reason", "payment failed: " + gp.errorDescription());
                if (onlineAttemptPending(payment)) {
                    markFailed(payment, gp.errorDescription());
                }
                return outcome(HttpStatus.PAYMENT_REQUIRED, false, "Payment failed"
                        + (gp.errorDescription() == null ? "." : ": " + gp.errorDescription()) + " You can try again.", booking);
            }
            default -> {
                return outcome(HttpStatus.CONFLICT, false,
                        "The payment isn't complete yet (status " + gp.status() + "). Please wait a moment and refresh.", booking);
            }
        }
    }

    // ------------------------------------------------------------ 3. failure reported by checkout

    /** Records a failed attempt (from Checkout's payment.failed event). Never confirms anything. */
    @Transactional
    public GuestBookingResponse recordFailure(CurrentUser user, Long bookingId, PaymentFailureRequest request) {
        Booking booking = bookingRepository.findForUserForUpdate(bookingId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + bookingId + " not found"));
        Payment payment = booking.getPayment();
        if (payment == null || !request.razorpayOrderId().equals(payment.getRazorpayOrderId())) {
            throw new BadRequestException("This payment doesn't belong to this booking.");
        }
        if (onlineAttemptPending(payment)) {
            markFailed(payment, request.description());
        }
        log.info("Checkout reported a failed attempt for booking {}: {}", bookingId, request.description());
        AuditLogger.failure("PAYMENT_ATTEMPT", "bookingId", bookingId, "orderId", request.razorpayOrderId(),
                "code", request.code(), "reason", request.description());
        bookingRepository.flush();
        return mapper.toDetail(booking);
    }

    // ------------------------------------------------------------ 4. webhooks

    /**
     * Verifies the webhook signature over the raw body, skips events already processed (by event id),
     * and applies captured / failed / refund events. Returns false for a duplicate delivery.
     */
    @Transactional
    public boolean handleWebhook(String rawBody, String signature, String eventId) {
        if (!signatureVerifier.isWebhookConfigured()) {
            throw new ServiceUnavailableException("Webhooks are not configured");
        }
        if (!signatureVerifier.isWebhookSignatureValid(rawBody, signature)) {
            log.warn("Rejected Razorpay webhook with an invalid signature");
            AuditLogger.failure("WEBHOOK_RECEIVED", "eventId", eventId, "reason", "invalid signature");
            throw new BadRequestException("Invalid webhook signature");
        }
        JsonNode root = jsonMapper.readTree(rawBody);
        String event = root.path("event").asString("");
        if (eventId != null && !eventId.isBlank()) {
            if (webhookEventRepository.existsByEventId(eventId)) {
                log.info("Ignoring duplicate Razorpay webhook {} ({})", eventId, event);
                return false;
            }
            PaymentWebhookEvent record = new PaymentWebhookEvent();
            record.setEventId(eventId);
            record.setEventType(event.isEmpty() ? "unknown" : event);
            webhookEventRepository.save(record);
        }
        log.info("Processing Razorpay webhook {} ({})", eventId, event);
        AuditLogger.success("WEBHOOK_RECEIVED", "eventId", eventId, "type", event);
        JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
        switch (event) {
            case "payment.captured", "order.paid" -> onCaptured(paymentEntity);
            case "payment.failed" -> onFailed(paymentEntity);
            case "refund.processed", "refund.failed" ->
                    onRefund(root.path("payload").path("refund").path("entity"), event.equals("refund.processed"));
            default -> log.debug("Ignoring Razorpay webhook event {}", event);
        }
        return true;
    }

    private void onCaptured(JsonNode entity) {
        Booking booking = lockBookingForOrder(entity.path("order_id").asString(null));
        if (booking == null) {
            return;
        }
        Payment payment = booking.getPayment();
        if (SETTLED.contains(payment.getPaymentStatus())) {
            return; // already handled by verify or an earlier delivery
        }
        GatewayPayment gp = new GatewayPayment(entity.path("id").asString(null), entity.path("order_id").asString(null),
                entity.path("amount").asLong(0), entity.path("currency").asString(null), entity.path("status").asString(null),
                null, null);
        String problem = mismatch(gp, payment);
        if (problem != null || !"captured".equals(gp.status())) {
            log.error("Webhook payment {} for booking {} not applied: {}", gp.id(), booking.getBookingId(),
                    problem == null ? "status " + gp.status() : problem);
            return;
        }
        applySuccessfulPayment(booking, payment, gp.id());
    }

    private void onFailed(JsonNode entity) {
        Booking booking = lockBookingForOrder(entity.path("order_id").asString(null));
        if (booking != null && onlineAttemptPending(booking.getPayment())) {
            markFailed(booking.getPayment(), entity.path("error_description").asString(null));
        }
    }

    private void onRefund(JsonNode refund, boolean processed) {
        String refundId = refund.path("id").asString(null);
        Payment payment = refundId == null ? null : paymentRepository.findByRazorpayRefundId(refundId).orElse(null);
        if (payment == null) {
            log.info("Refund webhook for unknown refund {}", refundId);
            return;
        }
        if (processed) {
            payment.setPaymentStatus(PaymentStatus.REFUNDED);
            log.info("Refund {} processed (payment {})", refundId, payment.getId());
            AuditLogger.success("REFUND_COMPLETED", "refundId", refundId, "paymentId", payment.getId());
        } else {
            // Not marked refunded: the money hasn't gone back. Needs attention in the Razorpay dashboard.
            payment.setFailureReason("Refund failed at the payment provider");
            log.error("Razorpay reports refund {} failed (payment {})", refundId, payment.getId());
        }
    }

    private Booking lockBookingForOrder(String orderId) {
        if (orderId == null) {
            return null;
        }
        Payment payment = paymentRepository.findByRazorpayOrderId(orderId).orElse(null);
        if (payment == null) {
            log.info("Webhook for unknown Razorpay order {}", orderId);
            return null;
        }
        Long bookingId = paymentRepository.findBookingId(payment.getId()).orElse(null);
        return bookingId == null ? null : bookingRepository.findByIdForUpdate(bookingId).orElse(null);
    }

    // ------------------------------------------------------------ shared

    /** Gateway payment must be for this order, for exactly the expected amount and currency. */
    private String mismatch(GatewayPayment gp, Payment payment) {
        if (gp.orderId() == null || !gp.orderId().equals(payment.getRazorpayOrderId())) {
            return "the payment is for a different order";
        }
        if (gp.amount() != toSubunits(payment.getAmount()) || gp.currency() == null
                || !gp.currency().equalsIgnoreCase(payment.getCurrency())) {
            return "the paid amount doesn't match the booking amount";
        }
        return null;
    }

    /**
     * Confirms the booking for a verified, captured payment. If the hold had already expired, the rooms
     * are re-taken when still available; otherwise (or for a cancelled booking) the payment is refunded
     * in full so the guest is never charged for a booking they don't have.
     */
    private VerifyOutcome applySuccessfulPayment(Booking booking, Payment payment, String gatewayPaymentId) {
        payment.setRazorpayPaymentId(gatewayPaymentId);
        payment.setFailureReason(null);
        payment.setPaymentStatus(PaymentStatus.CONFIRMED);
        payment.setMethod(PaymentMethod.ONLINE); // also when the guest had switched to pay-at-hotel meanwhile
        BookingStatus status = booking.getBookingStatus();
        if (status == BookingStatus.RESERVED || status == BookingStatus.ACCEPTED || status == BookingStatus.CONFIRMED) {
            booking.setBookingStatus(BookingStatus.CONFIRMED);
            log.info("Booking {} confirmed by payment {}", booking.getBookingId(), gatewayPaymentId);
            AuditLogger.success("PAYMENT_VERIFIED", "bookingId", booking.getBookingId(), "paymentId", gatewayPaymentId,
                    "amount", payment.getAmount(), "bookingStatus", "CONFIRMED");
            return outcome(HttpStatus.OK, true, "Payment successful. Your booking is confirmed.", booking);
        }
        // catalog re-takes the rooms only if the hotel and room type still exist and every night is still free.
        boolean stillBookable = status == BookingStatus.EXPIRED && !booking.getCheckInDate().isBefore(LocalDate.now())
                && catalogClient.reacquire(booking.getHoldKey());
        if (stillBookable) {
            booking.setBookingStatus(BookingStatus.CONFIRMED);
            booking.setHoldStatus(HoldStatus.HELD);
            log.info("Expired booking {} re-confirmed by late payment {}", booking.getBookingId(), gatewayPaymentId);
            AuditLogger.success("PAYMENT_VERIFIED", "bookingId", booking.getBookingId(), "paymentId", gatewayPaymentId,
                    "amount", payment.getAmount(), "bookingStatus", "CONFIRMED", "note", "late payment, rooms re-taken");
            return outcome(HttpStatus.OK, true, "Payment successful. Your booking is confirmed.", booking);
        }
        cancellationService.refundInFull(booking, payment);
        log.warn("Payment {} arrived for {} booking {}; refunded", gatewayPaymentId, status, booking.getBookingId());
        AuditLogger.success("PAYMENT_REFUNDED_UNBOOKABLE", "bookingId", booking.getBookingId(), "paymentId", gatewayPaymentId,
                "bookingStatus", status);
        return outcome(HttpStatus.CONFLICT, false, "Your reservation " + (status == BookingStatus.EXPIRED
                ? "expired and the rooms are no longer available" : "was cancelled")
                + ", so the payment is being refunded in full.", booking);
    }

    /**
     * An online payment still being attempted. A failed online attempt never overrides a booking the
     * guest has since switched to pay-at-hotel.
     */
    private static boolean onlineAttemptPending(Payment payment) {
        return payment.getPaymentStatus() == PaymentStatus.PENDING
                && GuestBookingMapper.methodOf(payment) == PaymentMethod.ONLINE;
    }

    private static void markFailed(Payment payment, String reason) {
        payment.setPaymentStatus(PaymentStatus.FAILED);
        String r = reason == null || reason.isBlank() ? "Payment failed" : reason.strip();
        payment.setFailureReason(r.length() > 250 ? r.substring(0, 250) : r);
    }

    private VerifyOutcome outcome(HttpStatus status, boolean confirmed, String message, Booking booking) {
        bookingRepository.flush();
        return new VerifyOutcome(status, new PaymentResultResponse(confirmed, message, mapper.toDetail(booking)));
    }

    private static String receipt(Booking booking) {
        return "bk_" + booking.getBookingId() + "_" + System.currentTimeMillis();
    }
}
