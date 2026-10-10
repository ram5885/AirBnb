package com.HotelManagement.AirBnb.dto.booking;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Reserve rooms (step 1 of the booking flow). guestIds are the user's saved guests (POST /api/v1/guests)
 * travelling on this booking; they may also be added later, but at least one is needed before payment.
 * The price is always computed on the server.
 */
public record CreateBookingRequest(
        @NotNull(message = "Hotel is required") Long hotelId,
        @NotNull(message = "Room type is required") Long roomId,
        @NotNull(message = "Check-in date is required") LocalDate checkInDate,
        @NotNull(message = "Check-out date is required") LocalDate checkOutDate,
        @NotNull(message = "Number of rooms is required")
        @Min(value = 1, message = "Book at least 1 room")
        @Max(value = 10, message = "At most 10 rooms per booking")
        Integer roomsCount,
        @Size(max = 50, message = "Too many guests")
        List<Long> guestIds
) {
}
