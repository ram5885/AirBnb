package com.HotelManagement.AirBnb.shared.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * First filter of every request: puts the request id (X-Request-Id from nginx / the gateway / the calling service,
 * or a new one) into the logging context (MDC "requestId", printed on every log line), echoes it in the response,
 * and writes one access-log line (logger "ACCESS" → access.log) when the request finishes. Health checks are not
 * logged. Never logs request bodies, tokens or headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_USER_ID = "userId";
    /**
     * On /internal/** calls: the user the calling service acts for. Used ONLY to label log lines (actor in the audit
     * log); never for authorization, which relies on the internal token and the JWT.
     */
    public static final String ACTING_USER_HEADER = "X-Acting-User";
    /** Request attribute set by UserMdcFilter once the JWT has been validated. */
    public static final String USER_ATTRIBUTE = "app.logging.userId";

    private static final Logger access = LoggerFactory.getLogger("ACCESS");
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || !SAFE_ID.matcher(requestId).matches()) {
            requestId = UUID.randomUUID().toString(); // missing or not a plain id: never echo arbitrary input
        }
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        String actingUser = request.getHeader(ACTING_USER_HEADER);
        if (request.getRequestURI().startsWith("/internal/") && actingUser != null && actingUser.matches("\\d{1,19}")) {
            MDC.put(MDC_USER_ID, actingUser);
            request.setAttribute(USER_ATTRIBUTE, actingUser);
        }
        long start = System.nanoTime();
        boolean health = request.getRequestURI().startsWith("/actuator/health");
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            log.error("Request {} {} failed: {}", request.getMethod(), request.getRequestURI(), e.toString());
            throw e;
        } finally {
            if (!health) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                Object user = request.getAttribute(USER_ATTRIBUTE);
                String query = request.getQueryString();
                access.info("ip={} user={} \"{} {}{}\" status={} {}ms",
                        clientIp(request), user == null ? "-" : user, request.getMethod(), request.getRequestURI(),
                        query == null ? "" : "?" + (query.length() > 300 ? query.substring(0, 300) + "..." : query),
                        response.getStatus(), ms);
            }
            MDC.remove(MDC_USER_ID);
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }
}
