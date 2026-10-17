package com.HotelManagement.AirBnb.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Details from Checkout's payment.failed event. Only ever used to record a failure, never a success. */
public record PaymentFailureRequest(
        @NotBlank(message = "razorpay_order_id is required") String razorpayOrderId,
        @Size(max = 100) String razorpayPaymentId,
        @Size(max = 100) String code,
        @Size(max = 500) String description
) {
}
