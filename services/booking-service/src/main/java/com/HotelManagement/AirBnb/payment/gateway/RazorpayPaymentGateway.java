package com.HotelManagement.AirBnb.payment.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Razorpay REST API (https://razorpay.com/docs/api/) over HTTPS with Basic auth (key id : key secret).
 * Credentials come from RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET. Live keys (rzp_live_...) are refused
 * unless razorpay.allow-live=true, so development can never take real money by accident.
 */
@Component
public class RazorpayPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentGateway.class);

    private final String keyId;
    private final boolean enabled;
    private final RestClient client;

    public RazorpayPaymentGateway(@Value("${razorpay.key-id:}") String keyId,
                                  @Value("${razorpay.key-secret:}") String keySecret,
                                  @Value("${razorpay.api-base-url:https://api.razorpay.com}") String baseUrl,
                                  @Value("${razorpay.allow-live:false}") boolean allowLive) {
        this.keyId = keyId.strip();
        boolean configured = !this.keyId.isEmpty() && !keySecret.isBlank();
        boolean live = this.keyId.startsWith("rzp_live_");
        if (configured && live && !allowLive) {
            log.error("Razorpay LIVE key configured but razorpay.allow-live is false; payments are disabled. "
                    + "Use test keys (rzp_test_...) for development.");
        } else if (!configured) {
            log.warn("Razorpay is not configured (set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET); online payments are disabled.");
        } else {
            log.info("Razorpay payments enabled in {} mode", live ? "LIVE" : "TEST");
        }
        this.enabled = configured && (!live || allowLive);
        String basic = Base64.getEncoder().encodeToString((this.keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .build();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String keyId() {
        return keyId;
    }

    @Override
    public GatewayOrder createOrder(long amount, String currency, String receipt, long bookingId) {
        Map<?, ?> body = post("/v1/orders", Map.of("amount", amount, "currency", currency, "receipt", receipt,
                "notes", Map.of("bookingId", String.valueOf(bookingId))), "create order");
        return new GatewayOrder(str(body, "id"), num(body, "amount"), str(body, "currency"), str(body, "status"));
    }

    @Override
    public GatewayPayment fetchPayment(String paymentId) {
        requireEnabled();
        try {
            return toPayment(client.get().uri("/v1/payments/{id}", paymentId).retrieve().body(Map.class));
        } catch (RestClientException e) {
            throw failure("fetch payment", e);
        }
    }

    @Override
    public GatewayPayment capture(String paymentId, long amount, String currency) {
        return toPayment(post("/v1/payments/" + paymentId + "/capture", Map.of("amount", amount, "currency", currency),
                "capture payment"));
    }

    @Override
    public GatewayRefund refund(String paymentId, long amount, String receipt) {
        Map<?, ?> body = post("/v1/payments/" + paymentId + "/refund", Map.of("amount", amount, "receipt", receipt),
                "refund payment");
        return new GatewayRefund(str(body, "id"), str(body, "payment_id"), num(body, "amount"), str(body, "status"));
    }

    private Map<?, ?> post(String path, Map<String, ?> payload, String action) {
        requireEnabled();
        try {
            return client.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().body(Map.class);
        } catch (RestClientException e) {
            throw failure(action, e);
        }
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new PaymentGatewayException("Online payments are not configured");
        }
    }

    private static PaymentGatewayException failure(String action, RestClientException e) {
        // Response bodies are Razorpay error descriptions (no credentials); keep them for diagnosis.
        String detail = e instanceof RestClientResponseException r
                ? r.getStatusCode().value() + " " + r.getResponseBodyAsString()
                : e.getClass().getSimpleName();
        log.warn("Razorpay {} failed: {}", action, detail);
        return new PaymentGatewayException("Payment gateway error while trying to " + action, e);
    }

    private static GatewayPayment toPayment(Map<?, ?> body) {
        return new GatewayPayment(str(body, "id"), str(body, "order_id"), num(body, "amount"), str(body, "currency"),
                str(body, "status"), str(body, "error_code"), str(body, "error_description"));
    }

    private static String str(Map<?, ?> body, String key) {
        Object v = body == null ? null : body.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static long num(Map<?, ?> body, String key) {
        Object v = body == null ? null : body.get(key);
        return v instanceof Number n ? n.longValue() : 0L;
    }
}
