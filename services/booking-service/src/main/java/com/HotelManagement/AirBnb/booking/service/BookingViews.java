package com.HotelManagement.AirBnb.booking.service;

import com.HotelManagement.AirBnb.booking.entity.Booking;
import com.HotelManagement.AirBnb.client.CatalogClient;
import com.HotelManagement.AirBnb.client.CatalogDtos.HotelSummary;
import com.HotelManagement.AirBnb.client.CatalogDtos.RoomSummary;
import com.HotelManagement.AirBnb.client.CatalogDtos.SummariesResponse;
import com.HotelManagement.AirBnb.shared.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Current hotel / room-type facts for bookings, from catalog-service in one call per page. Views prefer the live
 * values (renamed hotel, deleted hotel, current cover photo) and fall back to the snapshot stored on the booking
 * when catalog is unavailable; actions that depend on them (pay, accept) use {@link #live} and fail with 503.
 */
@Component
public class BookingViews {

    private static final Logger log = LoggerFactory.getLogger(BookingViews.class);

    public record HotelFacts(String name, String city, String state, boolean deleted, String coverPhotoUrl) {
    }

    public record RoomFacts(String type, Integer capacity, boolean deleted) {
    }

    /** Catalog facts for a set of bookings; unknown ids fall back to each booking's snapshot. */
    public static final class Catalog {

        static final Catalog EMPTY = new Catalog(Map.of(), Map.of());

        private final Map<Long, HotelSummary> hotels;
        private final Map<Long, RoomSummary> rooms;

        Catalog(Map<Long, HotelSummary> hotels, Map<Long, RoomSummary> rooms) {
            this.hotels = hotels;
            this.rooms = rooms;
        }

        public HotelFacts hotel(Booking b) {
            HotelSummary h = hotels.get(b.getHotelId());
            if (h == null) {
                return new HotelFacts(b.getHotelName(), b.getHotelCity(), b.getHotelState(), false, null);
            }
            return new HotelFacts(h.name(), h.city(), h.state(), h.deleted(), h.coverPhotoUrl());
        }

        public RoomFacts room(Booking b) {
            RoomSummary r = rooms.get(b.getRoomId());
            if (r == null) {
                return new RoomFacts(b.getRoomType(), b.getRoomCapacity(), false);
            }
            return new RoomFacts(r.type(), r.capacity(), r.deleted());
        }
    }

    private final CatalogClient catalogClient;

    public BookingViews(CatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    /** Best effort: when catalog can't be reached the snapshots are used. */
    public Catalog forBookings(Collection<Booking> bookings) {
        if (bookings.isEmpty()) {
            return Catalog.EMPTY;
        }
        try {
            return load(bookings);
        } catch (ServiceUnavailableException e) {
            log.warn("Showing booking snapshots: {}", e.getMessage());
            return Catalog.EMPTY;
        }
    }

    /** Strict: catalog must answer (used before actions whose rules depend on the hotel's current state). */
    public Catalog live(Booking booking) {
        log.debug("Loading live hotel facts for booking {}", booking.getBookingId());
        return load(List.of(booking));
    }

    private Catalog load(Collection<Booking> bookings) {
        SummariesResponse s = catalogClient.summaries(
                bookings.stream().map(Booking::getHotelId).distinct().toList(),
                bookings.stream().map(Booking::getRoomId).distinct().toList());
        return new Catalog(
                s.hotels().stream().collect(Collectors.toMap(HotelSummary::id, Function.identity(), (a, b) -> a)),
                s.rooms().stream().collect(Collectors.toMap(RoomSummary::id, Function.identity(), (a, b) -> a)));
    }
}
