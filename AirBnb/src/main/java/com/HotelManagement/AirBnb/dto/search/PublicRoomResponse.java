package com.HotelManagement.AirBnb.dto.search;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Public room type details, with a night-by-night breakdown when a stay was given. */
public record PublicRoomResponse(
        Long id,
        Long hotelId,
        String hotelName,
        String city,
        String state,
        String type,
        Integer capacity,
        BigDecimal basePrice,
        List<String> amenities,
        List<String> photoUrls,
        StayResponse stay,
        AvailabilityResponse availability,
        List<Night> nights
) {
    /** price is per room for that night; null when the night isn't open for sale. */
    public record Night(LocalDate date, BigDecimal price, int availableRooms, boolean bookable) {
    }
}
