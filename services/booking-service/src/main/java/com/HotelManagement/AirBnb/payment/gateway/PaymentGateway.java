package com.HotelManagement.AirBnb.payment.gateway;

/**
 * Server-side calls to the payment gateway (Razorpay). Amounts are in currency subunits (paise).
 * Implementations throw {@link PaymentGatewayException} when the gateway can't be reached or rejects a call.
 */
public interface PaymentGateway {

    /** True when API credentials are configured and allowed (test keys, or live keys explicitly enabled). */
    boolean isEnabled();

    /** Public key id the browser checkout needs. Never the secret. */
    String keyId();

    GatewayOrder createOrder(long amount, String currency, String receipt, long bookingId);

    GatewayPayment fetchPayment(String paymentId);

    GatewayPayment capture(String paymentId, long amount, String currency);

    GatewayRefund refund(String paymentId, long amount, String receipt);

    record GatewayOrder(String id, long amount, String currency, String status) {
    }

    /** status: created, authorized, captured, refunded or failed. */
    record GatewayPayment(String id, String orderId, long amount, String currency, String status,
                          String errorCode, String errorDescription) {
    }

    /** status: pending, processed or failed. */
    record GatewayRefund(String id, String paymentId, long amount, String status) {
    }
}
