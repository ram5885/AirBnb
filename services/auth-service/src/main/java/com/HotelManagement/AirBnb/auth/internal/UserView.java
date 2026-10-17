package com.HotelManagement.AirBnb.auth.internal;

import com.HotelManagement.AirBnb.auth.entity.Role;
import com.HotelManagement.AirBnb.auth.entity.User;

import java.util.Set;

/** Internal contract: public profile of an account (never the password hash). */
public record UserView(Long id, String name, String email, Set<Role> roles) {

    public static UserView from(User u) {
        return new UserView(u.getId(), u.getName(), u.getEmail(), u.getRoles());
    }
}
