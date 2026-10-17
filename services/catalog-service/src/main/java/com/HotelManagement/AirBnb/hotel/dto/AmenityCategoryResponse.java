package com.HotelManagement.AirBnb.hotel.dto;

import java.util.List;

public record AmenityCategoryResponse(String code, String label, List<AmenityOption> amenities) {

    public record AmenityOption(String code, String label) {
    }
}
