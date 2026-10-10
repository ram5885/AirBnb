package com.HotelManagement.AirBnb.dto.room;

import com.HotelManagement.AirBnb.dto.PhotoResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record RoomResponse(
        Long id,
        Long hotelId,
        String type,
        BigDecimal basePrice,
        Integer totalCount,
        Integer capacity,
        List<String> amenities,
        List<PhotoResponse> photos,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
