package com.HotelManagement.AirBnb.entity.enums;

/**
 * PENDING        - gateway order created, awaiting a successful payment (also after a failed attempt is retried).
 * CONFIRMED      - payment verified (signature, amount, currency and captured status) with the gateway.
 * CANCELLED      - booking cancelled or expired before any payment succeeded.
 * FAILED         - the latest payment attempt failed; the guest may retry while the hold lasts.
 * REFUND_PENDING - a full refund was requested from the gateway but isn't processed yet.
 * REFUNDED       - the gateway reports the refund as processed.
 */
public enum PaymentStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    FAILED,
    REFUND_PENDING,
    REFUNDED
}
