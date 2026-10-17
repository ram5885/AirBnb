package com.HotelManagement.AirBnb.hotel.hold;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Internal contract (booking-service -> catalog-service): take {@code roomsCount} rooms of a room type for every
 * night in [checkInDate, checkOutDate). guestCount is the size of the booking's party, checked against the room
 * type's capacity. Calling again with the same holdKey returns the existing hold (idempotent).
 */
public record HoldRequest(
        @NotNull UUID holdKey,
        @NotNull Long hotelId,
        @NotNull Long roomId,
        @NotNull LocalDate checkInDate,
        @NotNull LocalDate checkOutDate,
        @NotNull @Min(1) @Max(10) Integer roomsCount,
        @NotNull @Min(0) Integer guestCount
) {
}
