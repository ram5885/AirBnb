package com.HotelManagement.AirBnb.dto.booking;

import com.HotelManagement.AirBnb.entity.enums.BookingStatus;
import com.HotelManagement.AirBnb.entity.enums.Gender;
import com.HotelManagement.AirBnb.entity.enums.PaymentMethod;
import com.HotelManagement.AirBnb.entity.enums.PaymentStatus;

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
