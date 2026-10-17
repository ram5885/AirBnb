package com.HotelManagement.AirBnb.shared.security;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.HotelManagement.AirBnb.shared.exception.UnauthorizedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Resolves the caller from the validated JWT. Identity always comes from the token subject (signed by
 * auth-service), never from ids in the request body. Whether the account still exists is checked once at the
 * API gateway (the monolith's CurrentUserService did it here, against the shared user table).
 */
@Service
public class CurrentUserService {

    private static final Logger log = LoggerFactory.getLogger(CurrentUserService.class);

    public long requireId(Jwt jwt) {
        if (jwt == null) {
            log.debug("No authenticated user on a protected call");
            throw new UnauthorizedException("Please log in to continue");
        }
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("Please log in to continue");
        }
    }
}
