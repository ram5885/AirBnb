package com.HotelManagement.AirBnb.dto.booking;

import com.HotelManagement.AirBnb.entity.enums.BookingStatus;
import com.HotelManagement.AirBnb.entity.enums.PaymentMethod;
import com.HotelManagement.AirBnb.entity.enums.PaymentStatus;

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
