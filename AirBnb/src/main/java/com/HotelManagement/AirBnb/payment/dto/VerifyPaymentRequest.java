package com.HotelManagement.AirBnb.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** The three values Razorpay Checkout returns to the success handler, plus the booking they're for. */
public record VerifyPaymentRequest(
        @NotNull(message = "Booking is required") Long bookingId,
        @NotBlank(message = "razorpay_order_id is required") String razorpayOrderId,
        @NotBlank(message = "razorpay_payment_id is required") String razorpayPaymentId,
        @NotBlank(message = "razorpay_signature is required") String razorpaySignature
) {
}
