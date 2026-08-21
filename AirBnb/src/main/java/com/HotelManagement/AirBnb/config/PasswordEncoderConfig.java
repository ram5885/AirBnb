package com.HotelManagement.AirBnb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Exposes a single, shared {@link PasswordEncoder} bean backed by BCrypt.
 * BCrypt automatically salts each password and is safe to use directly
 * for hashing (signup) and verification (login) via {@code encode()} / {@code matches()}.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
