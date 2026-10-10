package com.HotelManagement.AirBnb.auth.service;

import com.HotelManagement.AirBnb.auth.entity.User;
import com.HotelManagement.AirBnb.auth.repository.UserRepository;
import com.HotelManagement.AirBnb.shared.exception.UnauthorizedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Resolves the caller from the validated JWT. Identity always comes from the token,
 * never from ids in the request body.
 */
@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User require(Jwt jwt) {
        if (jwt == null) {
            throw new UnauthorizedException("Please log in to continue");
        }
        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("Please log in to continue");
        }
        // The email check guards against a token outliving its user (e.g. after a DB reset reuses ids).
        return userRepository.findUserById(userId)
                .filter(u -> u.getEmail().equals(jwt.getClaimAsString("email")))
                .orElseThrow(() -> new UnauthorizedException("Your session has expired. Please log in again."));
    }
}
