package com.HotelManagement.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

/**
 * Gateway-level failures (downstream service down or too slow, unknown path) as {"error": ...} JSON with the right
 * status, never a stack trace or the framework's default error page. Errors returned by services pass through
 * untouched (they already use this shape).
 */
@Component
@Order(-2)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        String path = "req=" + exchange.getAttribute(RequestLoggingWebFilter.REQUEST_ID_ATTRIBUTE) + " "
                + exchange.getRequest().getMethod() + " " + exchange.getRequest().getPath().value();
        if (isTimeout(ex)) {
            log.warn("Timeout routing {}: {}", path, ex.toString());
            return JsonErrors.write(exchange.getResponse(), HttpStatus.GATEWAY_TIMEOUT,
                    "The server took too long to respond. Please try again.");
        }
        if (isConnectionFailure(ex)) {
            log.warn("Service unavailable for {}: {}", path, ex.toString());
            return JsonErrors.write(exchange.getResponse(), HttpStatus.SERVICE_UNAVAILABLE,
                    "This part of the site is temporarily unavailable. Please try again in a moment.");
        }
        if (ex instanceof ResponseStatusException rse) {
            HttpStatusCode code = rse.getStatusCode();
            HttpStatus status = HttpStatus.resolve(code.value());
            if (status == HttpStatus.NOT_FOUND) {
                return JsonErrors.write(exchange.getResponse(), status, "Not found");
            }
            if (status != null && status.is4xxClientError()) {
                return JsonErrors.write(exchange.getResponse(), status, "Invalid request");
            }
            if (status == HttpStatus.SERVICE_UNAVAILABLE || status == HttpStatus.BAD_GATEWAY) {
                return JsonErrors.write(exchange.getResponse(), HttpStatus.SERVICE_UNAVAILABLE,
                        "This part of the site is temporarily unavailable. Please try again in a moment.");
            }
            if (status == HttpStatus.GATEWAY_TIMEOUT) {
                return JsonErrors.write(exchange.getResponse(), status, "The server took too long to respond. Please try again.");
            }
        }
        log.error("Unhandled gateway error for {}", path, ex);
        return JsonErrors.write(exchange.getResponse(), HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.");
    }

    private static boolean isTimeout(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof TimeoutException || t.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isConnectionFailure(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ConnectException || t instanceof java.net.UnknownHostException
                    || t.getClass().getName().contains("PrematureCloseException")) {
                return true;
            }
        }
        return false;
    }
}
