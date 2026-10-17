package com.HotelManagement.AirBnb.booking.entity;

/**
 * Booking lifecycle (instant book):
 * RESERVED  - guest has started a booking; inventory is held for a limited time while they pay.
 * ACCEPTED  - hotel manager has approved a reserved booking (inventory still held, awaiting payment).
 * CONFIRMED - payment verified with the payment gateway.
 * CANCELLED - cancelled by the guest or hotel manager; inventory released (and payment refunded if paid).
 * EXPIRED   - the unpaid hold timed out; inventory released.
 */
public enum BookingStatus {
    RESERVED,
    ACCEPTED,
    CONFIRMED,
    CANCELLED,
    EXPIRED
}
