package com.HotelManagement.AirBnb.location.dto;

import com.HotelManagement.AirBnb.location.entity.Location;

public record LocationDto(Long id, String city, String state) {

    public static LocationDto from(Location location) {
        return new LocationDto(location.getId(), location.getCity(), location.getState());
    }
}
