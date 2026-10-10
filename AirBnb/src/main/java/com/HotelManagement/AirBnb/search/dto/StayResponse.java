package com.HotelManagement.AirBnb.search.dto;

import java.time.LocalDate;

/** The stay a search/availability answer is for. Check-in is inclusive, check-out exclusive. */
public record StayResponse(LocalDate checkinDate, LocalDate checkoutDate, long nights, int numberOfRooms) {
}
