package com.HotelManagement.AirBnb.review.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * A hotel's rating from its reviews: averageRating = sum of ratings / number of reviews, rounded to one
 * decimal (null without reviews); ratingDistribution maps each star value 5..1 to its review count.
 */
public record ReviewSummaryResponse(Long hotelId, BigDecimal averageRating, long totalReviews,
                                    Map<Integer, Long> ratingDistribution) {
}
