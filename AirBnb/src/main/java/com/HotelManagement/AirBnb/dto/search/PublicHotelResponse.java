package com.HotelManagement.AirBnb.dto.search;

import java.util.List;

/** Public hotel details. Deliberately excludes the hotel's contact person, phone and email. */
public record PublicHotelResponse(
        Long id,
        String name,
        String city,
        String state,
        String address,
        String landmark,
        List<String> amenities,
        List<String> photoUrls,
        StayResponse stay,
        List<PublicRoomTypeSummary> roomTypes
) {
}
