package com.HotelManagement.AirBnb.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Razorpay's documented signature checks (HMAC-SHA256, hex encoded), compared in constant time:
 * - checkout: HMAC(order_id + "|" + razorpay_payment_id, key secret) == razorpay_signature
 * - webhook:  HMAC(raw request body, webhook secret) == X-Razorpay-Signature header
 */
@Component
public class RazorpaySignatureVerifier {

    private final String keySecret;
    private final String webhookSecret;

    public RazorpaySignatureVerifier(@Value("${razorpay.key-secret:}") String keySecret,
                                     @Value("${razorpay.webhook-secret:}") String webhookSecret) {
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
    }

    public boolean isPaymentSignatureValid(String orderId, String paymentId, String signature) {
        if (keySecret.isBlank() || orderId == null || paymentId == null || signature == null) {
            return false;
        }
        return matches(hmacHex(orderId + "|" + paymentId, keySecret), signature);
    }

    public boolean isWebhookConfigured() {
        return !webhookSecret.isBlank();
    }

    public boolean isWebhookSignatureValid(String rawBody, String signature) {
        if (webhookSecret.isBlank() || rawBody == null || signature == null) {
            return false;
        }
        return matches(hmacHex(rawBody, webhookSecret), signature);
    }

    static String hmacHex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static boolean matches(String expectedHex, String actual) {
        return MessageDigest.isEqual(expectedHex.getBytes(StandardCharsets.UTF_8),
                actual.strip().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
