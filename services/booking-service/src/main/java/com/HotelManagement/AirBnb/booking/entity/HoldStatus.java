package com.HotelManagement.AirBnb.booking.entity;

/**
 * Where the booking's rooms are in catalog-service's inventory (see InventoryHoldService there):
 * HELD            - the rooms are taken (RESERVED, ACCEPTED, CONFIRMED bookings).
 * RELEASE_PENDING - the booking was cancelled/expired and committed; giving the rooms back is in progress
 *                   (retried by HoldReleaseJob until catalog confirms).
 * RELEASED        - catalog has given the rooms back.
 */
public enum HoldStatus {
    HELD,
    RELEASE_PENDING,
    RELEASED
}
