package com.HotelManagement.AirBnb.internal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Internal (service-to-service) request/response contracts of catalog-service. */
public final class InternalDtos {

    private InternalDtos() {
    }

    public record AttachRequest(@NotNull Long bookingId) {
    }

    public record ReleaseResponse(String status) {
    }

    public record ReacquireResponse(boolean reacquired) {
    }

    public record SummariesRequest(@Size(max = 500) List<Long> hotelIds, @Size(max = 500) List<Long> roomIds) {
    }

    /**
     * Current facts about a hotel for booking views. coverPhotoUrl is the public cover photo, null when the hotel
     * isn't publicly visible (inactive/deleted) or has no photos.
     */
    public record HotelSummary(Long id, String name, String city, String state, Long ownerId, boolean active,
                               boolean deleted, String coverPhotoUrl) {
    }

    public record RoomSummary(Long id, Long hotelId, String type, Integer capacity, boolean deleted) {
    }

    public record SummariesResponse(List<HotelSummary> hotels, List<RoomSummary> rooms) {
    }

    public record PublicHotel(Long id, String name) {
    }

    public record RatingUpdate(@NotNull Long hotelId, @NotNull @PositiveOrZero Long ratingSum,
                               @NotNull @PositiveOrZero Long ratingCount) {
    }
}
