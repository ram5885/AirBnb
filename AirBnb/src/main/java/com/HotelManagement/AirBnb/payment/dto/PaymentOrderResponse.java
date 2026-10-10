package com.HotelManagement.AirBnb.dto.payment;

import java.math.BigDecimal;

/** What the browser needs to open Razorpay Checkout. amount is in subunits (paise); never contains the secret. */
public record PaymentOrderResponse(
        Long bookingId,
        String keyId,
        String orderId,
        long amount,
        String currency,
        BigDecimal displayAmount,
        String name,
        String description,
        String customerName,
        String customerEmail
) {
}
