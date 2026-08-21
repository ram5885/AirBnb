package com.HotelManagement.AirBnb.dto;

import com.HotelManagement.AirBnb.entity.enums.Role;

import java.util.Set;

public class AuthResponse {

    private Long id;
    private String name;
    private String email;
    private Set<Role> roles;
    private String message;

    public AuthResponse() {
    }

    public AuthResponse(Long id, String name, String email, Set<Role> roles, String message) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.roles = roles;
        this.message = message;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
