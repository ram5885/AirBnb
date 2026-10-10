package com.HotelManagement.AirBnb.dto.inventory;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Applies to every inventory date from startDate to endDate (inclusive). Null fields are left unchanged. */
public record InventoryUpdateRequest(
        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate,

        Boolean closed,

        @DecimalMin(value = "0.50", message = "Surge factor must be at least 0.50")
        @DecimalMax(value = "5.00", message = "Surge factor must be at most 5.00")
        @Digits(integer = 1, fraction = 2, message = "Surge factor can have at most 2 decimal places")
        BigDecimal surgeFactor
) {
}
