package com.HotelManagement.AirBnb.review.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * The logged-in guest's confirmed bookings at a hotel and whether each can be reviewed now. Informational
 * only: submitting a review re-checks everything.
 *
 * @param bookings latest check-out first
 */
public record ReviewEligibilityResponse(Long hotelId, List<StayStatus> bookings) {

    /** REVIEWABLE (can be reviewed now), REVIEWED (see review), UPCOMING (before check-out), NOT_ELIGIBLE (see reason). */
    public enum Status { REVIEWABLE, REVIEWED, UPCOMING, NOT_ELIGIBLE }

    public record StayStatus(Long bookingId, String roomType, LocalDate checkInDate, LocalDate checkOutDate,
                             Status status, String reason, OwnReview review) {
    }

    public record OwnReview(Long id, int rating, String comment, java.time.LocalDateTime createdAt) {
    }
}
