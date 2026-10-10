package com.HotelManagement.AirBnb.dto.search;

import java.math.BigDecimal;

/**
 * Availability of one room type for the requested stay.
 * availableRooms is the fewest free rooms on any night (0 if a night is closed or not open for sale).
 * Prices are for the whole stay and all requested rooms; null when not every night is open.
 */
public record AvailabilityResponse(boolean available, int availableRooms, BigDecimal totalPrice,
                                   BigDecimal averageNightlyPrice) {
}
