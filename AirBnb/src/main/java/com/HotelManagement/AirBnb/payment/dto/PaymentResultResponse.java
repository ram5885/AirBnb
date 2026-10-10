package com.HotelManagement.AirBnb.payment.dto;

import com.HotelManagement.AirBnb.booking.dto.GuestBookingResponse;

/** Outcome of verifying a payment; confirmed is true only when the booking is CONFIRMED by this payment. */
public record PaymentResultResponse(boolean confirmed, String message, GuestBookingResponse booking) {
}
