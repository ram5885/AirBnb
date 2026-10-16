package com.HotelManagement.AirBnb.search.dto;

import java.util.List;

/**
 * One "Stays in {city}" row on the guest home page: hotels in the city that can take the stay.
 *
 * @param availableHotels how many of the city's hotels can take the stay ({@code hotels} may hold fewer)
 * @param hotels          the first of them by name, as in search results
 */
public record HotelSectionResponse(Long locationId, String city, String state, long availableHotels,
                                   List<HotelSearchResult> hotels) {
}
