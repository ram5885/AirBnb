package com.HotelManagement.AirBnb.hotel.dto;

public record PhotoResponse(Long id, String fileName, String contentType, Long sizeBytes, String url) {
}
