package com.HotelManagement.AirBnb;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class FakeServicesConfig {

    @Bean
    @Primary
    FakeCatalog fakeCatalog() {
        return new FakeCatalog();
    }

    @Bean
    @Primary
    FakeAuth fakeAuth() {
        return new FakeAuth();
    }
}
