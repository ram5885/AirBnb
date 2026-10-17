package com.HotelManagement.AirBnb.shared.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs inside the security chain right after the JWT was validated: puts the user id (token subject) into the
 * logging context (MDC "userId") and a request attribute for the access log. Deliberately not a @Component, so it
 * is only registered in the security chain (see SecurityConfig).
 */
public class UserMdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            String userId = jwt.getToken().getSubject();
            MDC.put(RequestLoggingFilter.MDC_USER_ID, userId);
            request.setAttribute(RequestLoggingFilter.USER_ATTRIBUTE, userId);
        }
        chain.doFilter(request, response);
    }
}
