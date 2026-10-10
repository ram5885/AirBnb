package com.HotelManagement.AirBnb.guest.dto;

import com.HotelManagement.AirBnb.guest.entity.Gender;
import com.HotelManagement.AirBnb.guest.entity.Guest;

public record GuestResponse(Long id, String name, Gender gender, Integer age) {

    public static GuestResponse from(Guest g) {
        return new GuestResponse(g.getId(), g.getName(), g.getGender(), g.getAge());
    }
}
