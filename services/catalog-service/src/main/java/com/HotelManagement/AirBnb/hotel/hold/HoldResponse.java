package com.HotelManagement.AirBnb.hotel.hold;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Internal contract: a hold plus the catalog facts booking-service keeps as a snapshot on the booking
 * (hotel owner, names, room capacity). amount = sum of each night's inventory price x roomsCount, computed here.
 */
public record HoldResponse(
        UUID holdKey,
        HoldStatus status,
        Long bookingId,
        Long hotelId,
        Long hotelOwnerId,
        String hotelName,
        String city,
        String state,
        Long roomId,
        String roomType,
        Integer roomCapacity,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        Integer roomsCount,
        BigDecimal amount
) {
}
