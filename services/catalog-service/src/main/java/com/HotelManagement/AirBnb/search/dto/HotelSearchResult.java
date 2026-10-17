package com.HotelManagement.AirBnb.search.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One hotel in guest search results. Prices are for the cheapest room type that can take the stay and
 * matches the search filters (price range, amenities): lowestNightlyPrice is per room per night (average
 * over the stay), lowestTotalPrice covers all requested rooms and nights; availableRoomTypes counts the room
 * types that qualify. averageRating (one decimal) and reviewCount come from the hotel's reviews
 * (averageRating is null without reviews).
 */
public record HotelSearchResult(
        Long id,
        String name,
        String city,
        String state,
        List<String> photoUrls,
        List<String> amenities,
        int availableRoomTypes,
        BigDecimal lowestNightlyPrice,
        BigDecimal lowestTotalPrice,
        BigDecimal averageRating,
        long reviewCount
) {
}
