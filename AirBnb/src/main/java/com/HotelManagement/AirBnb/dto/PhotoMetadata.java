package com.HotelManagement.AirBnb.dto;

/** Photo metadata only; bytes are served by the photo endpoints. ownerId is the hotel or room id. */
public record PhotoMetadata(Long id, Long ownerId, String fileName, String contentType, Long sizeBytes) {
}
