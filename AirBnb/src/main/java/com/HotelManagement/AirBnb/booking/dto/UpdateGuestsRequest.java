package com.HotelManagement.AirBnb.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateGuestsRequest(
        @NotNull(message = "Choose at least one guest")
        @Size(min = 1, max = 50, message = "Choose between 1 and 50 guests")
        List<Long> guestIds
) {
}
