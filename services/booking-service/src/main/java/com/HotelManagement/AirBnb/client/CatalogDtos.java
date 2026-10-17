package com.HotelManagement.AirBnb.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contracts of catalog-service's internal API (see docs/SERVICE_API_REFERENCE.md). Owned by this client copy. */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record HoldRequest(UUID holdKey, Long hotelId, Long roomId, LocalDate checkInDate, LocalDate checkOutDate,
                              Integer roomsCount, Integer guestCount) {
    }

    public record HoldResponse(UUID holdKey, String status, Long bookingId, Long hotelId, Long hotelOwnerId,
                               String hotelName, String city, String state, Long roomId, String roomType,
                               Integer roomCapacity, LocalDate checkInDate, LocalDate checkOutDate, Integer roomsCount,
                               BigDecimal amount) {
    }

    public record AttachRequest(Long bookingId) {
    }

    public record ReleaseResponse(String status) {
    }

    public record ReacquireResponse(boolean reacquired) {
    }

    public record SummariesRequest(List<Long> hotelIds, List<Long> roomIds) {
    }

    public record HotelSummary(Long id, String name, String city, String state, Long ownerId, boolean active,
                               boolean deleted, String coverPhotoUrl) {
    }

    public record RoomSummary(Long id, Long hotelId, String type, Integer capacity, boolean deleted) {
    }

    public record SummariesResponse(List<HotelSummary> hotels, List<RoomSummary> rooms) {
    }

    public record HotelCounts(long totalHotels, long activeHotels) {
    }
}
