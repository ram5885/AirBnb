package com.HotelManagement.AirBnb.auth.service;

import com.HotelManagement.AirBnb.auth.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final JwtEncoder jwtEncoder;
    private final Duration expiration;

    public JwtService(JwtEncoder jwtEncoder, @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.jwtEncoder = jwtEncoder;
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    /** Signed token whose subject is the user id; roles drive URL authorization. */
    public String issueToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(expiration))
                .claim("email", user.getEmail())
                .claim("roles", user.getRoles().stream().map(Enum::name).toList())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        log.debug("Issuing token for account {} valid until {}", user.getId(), claims.getExpiresAt());
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
