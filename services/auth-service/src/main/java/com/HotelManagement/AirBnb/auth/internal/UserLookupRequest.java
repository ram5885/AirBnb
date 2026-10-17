package com.HotelManagement.AirBnb.auth.internal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UserLookupRequest(@NotNull @Size(max = 500) List<Long> ids) {
}
