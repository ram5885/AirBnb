package com.HotelManagement.AirBnb.dto.booking;

import com.HotelManagement.AirBnb.dto.guest.GuestResponse;
import com.HotelManagement.AirBnb.entity.enums.BookingStatus;
import com.HotelManagement.AirBnb.entity.enums.PaymentMethod;
import com.HotelManagement.AirBnb.entity.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A booking as its guest sees it. guests is only filled on the detail endpoint.
 * holdExpiresAt is set while an unpaid RESERVED booking is holding rooms.
 * canPay/canCancel reflect the server's rules so the UI never offers an action the API would refuse.
 */
public record GuestBookingResponse(
        Long id,
        String reference,
        Long hotelId,
        String hotelName,
        String city,
        String state,
        String hotelPhotoUrl,
        Long roomId,
        String roomType,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        long nights,
        Integer roomsCount,
        long guestCount,
        int maxGuests,
        List<GuestResponse> guests,
        BigDecimal amount,
        String currency,
        BookingStatus status,
        PaymentStatus paymentStatus,
        PaymentMethod paymentMethod,
        LocalDateTime holdExpiresAt,
        boolean canPay,
        boolean canCancel,
        boolean canEditGuests,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
