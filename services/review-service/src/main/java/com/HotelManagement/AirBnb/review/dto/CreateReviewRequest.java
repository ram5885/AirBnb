package com.HotelManagement.AirBnb.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Review a completed stay. The reviewer is always the logged-in user (never taken from the body), and
 * the booking must be theirs, at this hotel and completed; the comment is trimmed and must then be
 * REVIEW_MIN-REVIEW_MAX characters.
 */
public record CreateReviewRequest(
        @NotNull(message = "Choose the stay you're reviewing")
        Long bookingId,

        @NotNull(message = "Choose a rating from 1 to 5 stars")
        @Min(value = 1, message = "Choose a rating from 1 to 5 stars")
        @Max(value = 5, message = "Choose a rating from 1 to 5 stars")
        Integer rating,

        @NotBlank(message = "Write a few words about your stay")
        @Size(max = CreateReviewRequest.COMMENT_MAX, message = "Keep your review under " + CreateReviewRequest.COMMENT_MAX + " characters")
        String comment
) {
    public static final int COMMENT_MIN = 10;
    public static final int COMMENT_MAX = 1000;
}
