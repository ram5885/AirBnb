package com.HotelManagement.AirBnb.internal;

import com.HotelManagement.AirBnb.booking.entity.BookingStatus;
import com.HotelManagement.AirBnb.payment.entity.PaymentStatus;

import java.time.LocalDate;

/** Internal contract (booking-service -> review-service): the facts that decide whether a stay can be reviewed. */
public record StayView(Long bookingId, Long userId, Long hotelId, String roomType, LocalDate checkInDate,
                       LocalDate checkOutDate, BookingStatus status, PaymentStatus paymentStatus) {
}
