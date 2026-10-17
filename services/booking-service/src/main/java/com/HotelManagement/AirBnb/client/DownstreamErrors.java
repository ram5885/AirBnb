package com.HotelManagement.AirBnb.client;

import com.HotelManagement.AirBnb.shared.exception.BadRequestException;
import com.HotelManagement.AirBnb.shared.exception.ConflictException;
import com.HotelManagement.AirBnb.shared.exception.ResourceNotFoundException;
import com.HotelManagement.AirBnb.shared.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a failed call to another service into this service's exceptions: the other service's 400/404/409
 * (with its {"error": ...} message, which is user-facing) keep their status and message; anything else
 * (connection refused, timeout, 5xx, 401 from a misconfigured internal token) becomes 503 with a generic message.
 */
final class DownstreamErrors {

    private static final Logger log = LoggerFactory.getLogger(DownstreamErrors.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private DownstreamErrors() {
    }

    static RuntimeException translate(String service, String action, RestClientException e) {
        if (e instanceof RestClientResponseException r) {
            int status = r.getStatusCode().value();
            String message = errorMessage(r.getResponseBodyAsString());
            if (message != null) {
                switch (status) {
                    case 400 -> {
                        return new BadRequestException(message);
                    }
                    case 404 -> {
                        return new ResourceNotFoundException(message);
                    }
                    case 409 -> {
                        return new ConflictException(message);
                    }
                    default -> {
                    }
                }
            }
            log.warn("{} {} failed: HTTP {} {}", service, action, status, r.getResponseBodyAsString());
        } else {
            log.warn("{} {} failed: {}", service, action, e.toString());
        }
        return new ServiceUnavailableException(unavailableMessage(service));
    }

    static String unavailableMessage(String service) {
        return switch (service) {
            case "catalog-service" -> "Hotel information is temporarily unavailable. Please try again in a moment.";
            case "auth-service" -> "Account information is temporarily unavailable. Please try again in a moment.";
            default -> "A required service is temporarily unavailable. Please try again in a moment.";
        };
    }

    private static String errorMessage(String body) {
        try {
            JsonNode node = JSON.readTree(body);
            JsonNode error = node == null ? null : node.get("error");
            return error == null || error.isNull() ? null : error.asString();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
