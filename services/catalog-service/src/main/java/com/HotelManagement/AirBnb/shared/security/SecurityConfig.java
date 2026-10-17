package com.HotelManagement.AirBnb.shared.security;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.HotelManagement.AirBnb.shared.logging.UserMdcFilter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Stateless JWT validation for catalog-service (tokens are issued by auth-service with the shared JWT_SECRET).
 * The API gateway enforces the same rules first; this is defense in depth for the service itself.
 * Roles come from the token's "roles" claim (e.g. HOTEL_MANAGER -> ROLE_HOTEL_MANAGER).
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // no cookies/sessions: bearer tokens only
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error", "/actuator/health/**").permitAll()
                        // Service-to-service API: authenticated by X-Internal-Token (InternalApiFilter), not by JWT.
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/locations/**", "/api/v1/amenities").permitAll()
                        // Public hotel search/details/photos: only active hotels, no private contact data.
                        .requestMatchers(HttpMethod.GET, "/api/v1/hotels/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("HOTEL_MANAGER")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint((req, res, ex) ->
                                writeError(res, HttpStatus.UNAUTHORIZED, "Please log in to continue"))
                        .accessDeniedHandler((req, res, ex) ->
                                writeError(res, HttpStatus.FORBIDDEN, "You are not allowed to perform this action")))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                writeError(res, HttpStatus.UNAUTHORIZED, "Please log in to continue"))
                        .accessDeniedHandler((req, res, ex) ->
                                writeError(res, HttpStatus.FORBIDDEN, "You are not allowed to perform this action")))
                // The validated user id goes onto every log line and into the access log.
                .addFilterAfter(new UserMdcFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String secret) {
        return NimbusJwtDecoder.withSecretKey(secretKey(secret)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    static SecretKey secretKey(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes for HS256");
        }
        log.info("JWT validation configured (HS256, {}-byte key)", bytes.length);
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    private static void writeError(jakarta.servlet.http.HttpServletResponse res, HttpStatus status, String message)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
