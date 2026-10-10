package com.HotelManagement.AirBnb.dto.hotel;

import java.time.LocalDateTime;

public record HotelSummaryResponse(
        Long id,
        String name,
        String city,
        String state,
        Boolean active,
        String coverPhotoUrl,
        int photoCount,
        long roomTypeCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
