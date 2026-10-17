package com.HotelManagement.AirBnb.booking.config;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables @Scheduled jobs (HoldExpiryService releases expired unpaid reservations every minute). */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    private static final Logger log = LoggerFactory.getLogger(SchedulingConfig.class);

    public SchedulingConfig() {
        log.info("Background jobs enabled: hold expiry (1 min), release retry (1 min), hold reconciliation (5 min)");
    }
}
