package com.HotelManagement.AirBnb.hotel.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Room type create/update payload (the "data" part of the multipart request). */
public record RoomRequest(
        @NotBlank(message = "Room type name is required")
        @Size(max = 100, message = "Room type name must be at most 100 characters")
        String type,

        @NotNull(message = "Base price is required")
        @DecimalMin(value = "1.00", message = "Base price must be at least 1.00")
        @DecimalMax(value = "1000000.00", message = "Base price must be at most 1,000,000")
        @Digits(integer = 7, fraction = 2, message = "Base price can have at most 2 decimal places")
        BigDecimal basePrice,

        @NotNull(message = "Number of rooms is required")
        @Min(value = 1, message = "There must be at least 1 room of this type")
        @Max(value = 500, message = "At most 500 rooms per room type")
        Integer totalCount,

        @NotNull(message = "Capacity is required")
        @Min(value = 1, message = "Capacity must be at least 1 guest")
        @Max(value = 20, message = "Capacity must be at most 20 guests")
        Integer capacity,

        @Size(max = 60, message = "Too many amenities selected")
        List<String> amenities,

        // Update only: ids of existing photos to remove.
        List<Long> removedPhotoIds
) {
}
