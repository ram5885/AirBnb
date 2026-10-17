package com.HotelManagement.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Edge security: the monolith's SecurityConfig rules, enforced before anything is routed. JWTs (HS256, issued by
 * auth-service) are validated here and again by each service; roles come from the "roles" claim. Paths that
 * aren't public need a valid token whose account still exists (SessionCheckFilter). /internal/** is never
 * reachable from outside.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewaySecurityConfig.class);

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, SessionCheckFilter sessionCheck) {
        // Rejections decided here (not by a service) are marked, so the edge audit log records only those.
        ServerAuthenticationEntryPoint unauthorized = (exchange, ex) -> {
            exchange.getAttributes().put(RequestLoggingWebFilter.REJECTED_ATTRIBUTE, "AUTHENTICATION_REJECTED");
            return JsonErrors.write(exchange.getResponse(), HttpStatus.UNAUTHORIZED, "Please log in to continue");
        };
        ServerAccessDeniedHandler forbidden = (exchange, ex) -> {
            exchange.getAttributes().put(RequestLoggingWebFilter.REJECTED_ATTRIBUTE, "ACCESS_DENIED");
            return JsonErrors.write(exchange.getResponse(), HttpStatus.FORBIDDEN, "You are not allowed to perform this action");
        };
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable) // no cookies/sessions: bearer tokens only
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .cors(cors -> {
                })
                .authorizeExchange(auth -> auth
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/internal/**").denyAll()
                        .pathMatchers("/api/auth/**", "/actuator/health/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/locations", "/api/locations/**", "/api/v1/amenities").permitAll()
                        // Writing reviews (and checking whether one can be written) is for guests with completed
                        // stays; these come before the public GET rule below, which covers reading reviews.
                        .pathMatchers(HttpMethod.POST, "/api/v1/hotels/*/reviews").hasRole("GUEST")
                        .pathMatchers(HttpMethod.GET, "/api/v1/hotels/*/reviews/eligibility").hasRole("GUEST")
                        // Public hotel search/details/photos/reviews.
                        .pathMatchers(HttpMethod.GET, "/api/v1/hotels/**").permitAll()
                        .pathMatchers("/api/v1/admin/**").hasRole("HOTEL_MANAGER")
                        // Razorpay server-to-server callbacks: authenticated by their HMAC signature (booking-service).
                        .pathMatchers(HttpMethod.POST, "/api/v1/payments/webhook").permitAll()
                        .pathMatchers("/api/v1/bookings/**", "/api/v1/guests/**", "/api/v1/payments/**").hasRole("GUEST")
                        .anyExchange().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
                .addFilterAfter(sessionCheck, SecurityWebFiltersOrder.AUTHENTICATION);
        return http.build();
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) must be at least 32 bytes for HS256");
        }
        SecretKey key = new SecretKeySpec(bytes, "HmacSHA256");
        log.info("JWT validation configured (HS256, {}-byte key)", bytes.length);
        return NimbusReactiveJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors-allowed-origins}") String origins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        cors.setAllowCredentials(true);
        log.info("CORS allowed origins: {}", cors.getAllowedOrigins());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    private static ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
