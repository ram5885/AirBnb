package com.HotelManagement.AirBnb.dto.payment;

import com.HotelManagement.AirBnb.dto.booking.GuestBookingResponse;

/** Outcome of verifying a payment; confirmed is true only when the booking is CONFIRMED by this payment. */
public record PaymentResultResponse(boolean confirmed, String message, GuestBookingResponse booking) {
}
