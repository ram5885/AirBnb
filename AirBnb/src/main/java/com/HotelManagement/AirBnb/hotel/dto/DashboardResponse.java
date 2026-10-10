package com.HotelManagement.AirBnb.dto.hotel;

import java.util.Map;

public record DashboardResponse(long totalHotels, long activeHotels, Map<String, Long> bookingsByStatus) {
}
