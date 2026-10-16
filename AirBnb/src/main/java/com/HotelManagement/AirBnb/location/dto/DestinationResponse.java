package com.HotelManagement.AirBnb.location.dto;

/**
 * A city with listed hotels, for the home page's "Explore stays by city" row.
 *
 * @param hotelCount  active, non-deleted hotels in the city that have at least one room type
 * @param photoUrl    the city's own photo, else a photo of one of its hotels, else null
 * @param photoCredit author and licence of the city photo (null when photoUrl is a hotel photo or null)
 */
public record DestinationResponse(Long locationId, String city, String state, long hotelCount,
                                  String photoUrl, String photoCredit) {
}
