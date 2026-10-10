package com.HotelManagement.AirBnb.booking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables @Scheduled jobs (HoldExpiryService releases expired unpaid reservations every minute). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
