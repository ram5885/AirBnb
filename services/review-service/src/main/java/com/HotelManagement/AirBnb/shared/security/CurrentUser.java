package com.HotelManagement.AirBnb.shared.security;

/**
 * The authenticated caller, from the validated JWT (subject = auth-service user id, "email" claim). Replaces
 * the monolith's User entity in service signatures: user data is owned by auth-service.
 */
public final class CurrentUser {

    private final Long id;
    private final String email;

    public CurrentUser(Long id, String email) {
        this.id = id;
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }
}
