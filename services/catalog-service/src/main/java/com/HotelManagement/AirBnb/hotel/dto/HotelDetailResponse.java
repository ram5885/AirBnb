package com.HotelManagement.AirBnb.hotel.dto;

import com.HotelManagement.AirBnb.hotel.dto.PhotoResponse;

import java.time.LocalDateTime;
import java.util.List;

public record HotelDetailResponse(
        Long id,
        String name,
        Long locationId,
        String city,
        String state,
        ContactInfoResponse contactInfo,
        List<String> amenities,
        Boolean active,
        List<PhotoResponse> photos,
        long roomTypeCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
