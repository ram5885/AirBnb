package com.HotelManagement.AirBnb.dto.hotel;

import jakarta.validation.constraints.NotNull;

public record HotelStatusRequest(@NotNull(message = "Active status is required") Boolean active) {
}
