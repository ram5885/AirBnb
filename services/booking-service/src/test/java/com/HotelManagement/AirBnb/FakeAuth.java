package com.HotelManagement.AirBnb;

import com.HotelManagement.AirBnb.client.AuthClient;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory stand-in for auth-service's internal user lookup. */
public class FakeAuth extends AuthClient {

    final Map<Long, UserView> users = new ConcurrentHashMap<>();

    public FakeAuth() {
        super(RestClient.create());
    }

    @Override
    public Optional<UserView> findUser(Long id) {
        return Optional.ofNullable(users.get(id));
    }
}
