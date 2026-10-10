package com.HotelManagement.AirBnb.booking.dto;

import com.HotelManagement.AirBnb.booking.entity.BookingStatus;
import com.HotelManagement.AirBnb.guest.entity.Gender;
import com.HotelManagement.AirBnb.payment.entity.PaymentMethod;
import com.HotelManagement.AirBnb.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record BookingDetailResponse(
        Long id,
        Long hotelId,
        String hotelName,
        String hotelCity,
        boolean hotelDeleted,
        Long roomId,
        String roomType,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        long nights,
        Integer roomsCount,
        BookingStatus status,
        String bookedByName,
        String bookedByEmail,
        List<GuestInfo> guests,
        BigDecimal amount,
        PaymentStatus paymentStatus,
        PaymentMethod paymentMethod,
        BigDecimal paymentAmount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record GuestInfo(String name, Gender gender, Integer age) {
    }
}
