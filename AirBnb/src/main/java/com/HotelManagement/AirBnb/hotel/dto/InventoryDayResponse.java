package com.HotelManagement.AirBnb.hotel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryDayResponse(
        LocalDate date,
        int totalCount,
        int bookedCount,
        int available,
        boolean closed,
        BigDecimal surgeFactor,
        BigDecimal price
) {
}
