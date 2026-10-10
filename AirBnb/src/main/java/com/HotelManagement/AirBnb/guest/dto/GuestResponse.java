package com.HotelManagement.AirBnb.dto.guest;

import com.HotelManagement.AirBnb.entity.Guest;
import com.HotelManagement.AirBnb.entity.enums.Gender;

public record GuestResponse(Long id, String name, Gender gender, Integer age) {

    public static GuestResponse from(Guest g) {
        return new GuestResponse(g.getId(), g.getName(), g.getGender(), g.getAge());
    }
}
