package com.HotelManagement.AirBnb.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Service-to-service endpoints (/internal/**) are never routed by the API gateway and the service port is not
 * published, but as defense in depth every call must also carry the shared X-Internal-Token
 * (INTERNAL_API_TOKEN). Compared in constant time.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10) // after RequestLoggingFilter, so rejected calls are access-logged
public class InternalApiFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";

    private static final Logger log = LoggerFactory.getLogger(InternalApiFilter.class);

    private final byte[] token;

    public InternalApiFilter(@Value("${app.internal-api-token}") String token) {
        if (token == null || token.length() < 16) {
            throw new IllegalStateException("app.internal-api-token (INTERNAL_API_TOKEN) must be at least 16 characters");
        }
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        if (supplied == null || !MessageDigest.isEqual(token, supplied.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Rejected internal call {} {} from {}: {} internal token", request.getMethod(),
                    request.getRequestURI(), request.getRemoteAddr(), supplied == null ? "missing" : "invalid");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Internal API token missing or invalid\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
