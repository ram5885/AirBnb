package com.HotelManagement.AirBnb.client;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/** auth-service's internal user lookup (name and email of an account; never the password). */
@Component
public class AuthClient {

    private static final Logger log = LoggerFactory.getLogger(AuthClient.class);

    public record UserView(Long id, String name, String email) {
    }

    private final RestClient client;

    public AuthClient(@Qualifier("authRestClient") RestClient client) {
        this.client = client;
    }

    /** The account, or empty when it no longer exists. */
    public Optional<UserView> findUser(Long id) {
        log.debug("Looking up user {} in auth-service", id);
        try {
            return Optional.ofNullable(client.get().uri("/internal/users/{id}", id).retrieve().body(UserView.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw DownstreamErrors.translate("auth-service", "user lookup", e);
        }
    }
}
