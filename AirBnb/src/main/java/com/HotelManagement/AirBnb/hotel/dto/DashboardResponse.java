package com.HotelManagement.AirBnb.hotel.dto;

import java.util.Map;

public record DashboardResponse(long totalHotels, long activeHotels, Map<String, Long> bookingsByStatus) {
}
