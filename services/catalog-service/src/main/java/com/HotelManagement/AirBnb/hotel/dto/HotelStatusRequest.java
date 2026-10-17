package com.HotelManagement.AirBnb.hotel.dto;

import jakarta.validation.constraints.NotNull;

public record HotelStatusRequest(@NotNull(message = "Active status is required") Boolean active) {
}
