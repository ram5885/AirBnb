package com.HotelManagement.AirBnb.shared.security;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.HotelManagement.AirBnb.shared.exception.UnauthorizedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Resolves the caller from the validated JWT. Identity always comes from the token subject (signed by
 * auth-service), never from ids in the request body. Whether the account still exists is checked at the API
 * gateway; booking creation also loads the profile from auth-service (see BookingService).
 */
@Service
public class CurrentUserService {

    private static final Logger log = LoggerFactory.getLogger(CurrentUserService.class);

    public CurrentUser require(Jwt jwt) {
        if (jwt == null) {
            log.debug("No authenticated user on a protected call");
            throw new UnauthorizedException("Please log in to continue");
        }
        try {
            return new CurrentUser(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("email"));
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("Please log in to continue");
        }
    }
}
