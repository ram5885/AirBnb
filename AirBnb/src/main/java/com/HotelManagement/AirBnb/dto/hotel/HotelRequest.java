package com.HotelManagement.AirBnb.dto.hotel;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Create/update payload (the "data" part of the multipart request). Owner, ids and
 * timestamps are deliberately absent so they cannot be mass-assigned by clients.
 */
public record HotelRequest(
        @NotBlank(message = "Hotel name is required")
        @Size(max = 120, message = "Hotel name must be at most 120 characters")
        String name,

        @NotBlank(message = "State is required")
        String state,

        @NotBlank(message = "City is required")
        String city,

        @NotBlank(message = "Address is required")
        @Size(max = 255, message = "Address must be at most 255 characters")
        String address,

        @Size(max = 255, message = "Landmark must be at most 255 characters")
        String landmark,

        @NotBlank(message = "Contact person is required")
        @Size(max = 100, message = "Contact person must be at most 100 characters")
        String contactPerson,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^(\\+91[\\- ]?)?[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number")
        String phoneNumber,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 254, message = "Email must be at most 254 characters")
        String email,

        @Size(max = 60, message = "Too many amenities selected")
        List<String> amenities,

        @NotNull(message = "Active status is required")
        Boolean active,

        // Update only: ids of existing photos to remove.
        List<Long> removedPhotoIds
) {
}
