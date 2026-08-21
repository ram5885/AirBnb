package com.HotelManagement.AirBnb.dto;

import com.HotelManagement.AirBnb.entity.enums.Role;

public class SignupRequest {

    private String name;
    private String email;
    private String password;

    // Which of the 3 user types this account should be created as:
    // GUEST (normal user), HOTEL_MANAGER, or ADMIN.
    private Role role;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
