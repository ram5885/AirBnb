package com.HotelManagement.AirBnb.search.service;

/**
 * Stay limits shared by search and inventory holds (the monolith kept them on BookingService; booking-service
 * applies the same values to booking requests).
 */
public final class StayRules {

    private StayRules() {
    }

    /** Longest stay that can be booked (and searched). */
    public static final int MAX_NIGHTS = 30;

    /** Most rooms in one booking (and search). */
    public static final int MAX_ROOMS = 10;
}
