package com.HotelManagement.AirBnb.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(PasswordEncoderConfig.class);

    @Bean
    public PasswordEncoder passwordEncoder() {
        log.debug("Passwords are hashed with BCrypt");
        return new BCryptPasswordEncoder();
    }
}
