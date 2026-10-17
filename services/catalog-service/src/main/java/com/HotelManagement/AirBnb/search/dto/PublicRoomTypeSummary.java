package com.HotelManagement.AirBnb.search.dto;

import java.math.BigDecimal;
import java.util.List;

/** Room type as shown to guests; availability is null when no stay was given. */
public record PublicRoomTypeSummary(
        Long id,
        String type,
        Integer capacity,
        BigDecimal basePrice,
        List<String> amenities,
        List<String> photoUrls,
        AvailabilityResponse availability
) {
}
