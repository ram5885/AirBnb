package com.HotelManagement.AirBnb.dto;

import com.HotelManagement.AirBnb.entity.Location;

public record LocationDto(Long id, String city, String state) {

    public static LocationDto from(Location location) {
        return new LocationDto(location.getId(), location.getCity(), location.getState());
    }
}
