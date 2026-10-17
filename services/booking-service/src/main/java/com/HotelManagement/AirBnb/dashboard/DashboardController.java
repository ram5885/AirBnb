package com.HotelManagement.AirBnb.dashboard;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.HotelManagement.AirBnb.booking.entity.BookingStatus;
import com.HotelManagement.AirBnb.booking.repository.BookingRepository;
import com.HotelManagement.AirBnb.client.CatalogClient;
import com.HotelManagement.AirBnb.client.CatalogDtos.HotelCounts;
import com.HotelManagement.AirBnb.shared.security.CurrentUserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manager dashboard. Served by booking-service because it is mostly booking counts; the hotel counts come from
 * catalog-service (keeping the dependency one-way: booking -> catalog).
 */
@RestController
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final BookingRepository bookingRepository;
    private final CatalogClient catalogClient;
    private final CurrentUserService currentUser;

    public DashboardController(BookingRepository bookingRepository, CatalogClient catalogClient,
                               CurrentUserService currentUser) {
        this.bookingRepository = bookingRepository;
        this.catalogClient = catalogClient;
        this.currentUser = currentUser;
    }

    /**
     * <b>GET /api/v1/admin/dashboard</b> — summary numbers for the manager dashboard.
     * <p>
     * Counts the caller's hotels (total and active, excluding deleted ones) and their bookings per status
     * (RESERVED, ACCEPTED, CONFIRMED, CANCELLED, EXPIRED).
     * <p>
     * Access: HOTEL_MANAGER. 200 OK. 503: hotel counts unavailable.
     *
     * @return hotel and booking counts for the caller
     */
    @GetMapping("/api/v1/admin/dashboard")
    @Transactional(readOnly = true)
    public DashboardResponse dashboard(@AuthenticationPrincipal Jwt jwt) {
        log.debug("GET /api/v1/admin/dashboard");
        Long ownerId = currentUser.require(jwt).getId();
        Map<BookingStatus, Long> counts = new EnumMap<>(BookingStatus.class);
        for (BookingStatus s : BookingStatus.values()) {
            counts.put(s, 0L);
        }
        for (Object[] row : bookingRepository.countByStatusForOwner(ownerId)) {
            counts.put(BookingStatus.valueOf((String) row[0]), ((Number) row[1]).longValue());
        }
        Map<String, Long> byName = new LinkedHashMap<>();
        counts.forEach((k, v) -> byName.put(k.name(), v));
        HotelCounts hotels = catalogClient.hotelCounts(ownerId);
        return new DashboardResponse(hotels.totalHotels(), hotels.activeHotels(), byName);
    }
}
