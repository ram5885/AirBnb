package com.HotelManagement.AirBnb.guest.dto;

import com.HotelManagement.AirBnb.guest.entity.Gender;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A person travelling on the user's bookings (Guest entity). */
public record GuestRequest(
        @NotBlank(message = "Guest name is required")
        @Size(max = 100, message = "Guest name must be at most 100 characters")
        String name,

        @NotNull(message = "Gender is required")
        Gender gender,

        @NotNull(message = "Age is required")
        @Min(value = 0, message = "Age can't be negative")
        @Max(value = 120, message = "Enter a valid age")
        Integer age
) {
}
