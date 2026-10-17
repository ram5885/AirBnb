package com.HotelManagement.AirBnb;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Tests start from an empty schema: clean (this service's schema only, in the test database) then migrate. */
@TestConfiguration
public class TestFlywayConfig {

    @Bean
    FlywayMigrationStrategy cleanMigrate() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
