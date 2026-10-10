package com.HotelManagement.AirBnb.booking.dto;

import com.HotelManagement.AirBnb.booking.entity.BookingStatus;
import com.HotelManagement.AirBnb.payment.entity.PaymentMethod;
import com.HotelManagement.AirBnb.payment.entity.PaymentStatus;

import java.math.BigDecimal;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Booking list row. Only the booker's name is included; guest details are on the detail view. */
public record BookingResponse(
        Long id,
        Long hotelId,
        String hotelName,
        Long roomId,
        String roomType,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        long nights,
        Integer roomsCount,
        long guestCount,
        BookingStatus status,
        BigDecimal amount,
        PaymentStatus paymentStatus,
        PaymentMethod paymentMethod,
        String bookedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
