package com.HotelManagement.AirBnb.review.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One public review. Only the reviewer's first name is shown (no email or other account details).
 * Every review comes from a completed booking, so verifiedStay is always true; stayedOn is the
 * check-in date of that booking and roomType the room type stayed in.
 */
public record ReviewResponse(Long id, int rating, String comment, LocalDateTime createdAt, String reviewerName,
                             LocalDate stayedOn, String roomType, boolean verifiedStay) {
}
