package com.HotelManagement.AirBnb;

import com.HotelManagement.AirBnb.client.CatalogClient;
import com.HotelManagement.AirBnb.client.CatalogDtos.HoldRequest;
import com.HotelManagement.AirBnb.client.CatalogDtos.HoldResponse;
import com.HotelManagement.AirBnb.client.CatalogDtos.HotelCounts;
import com.HotelManagement.AirBnb.client.CatalogDtos.HotelSummary;
import com.HotelManagement.AirBnb.client.CatalogDtos.RoomSummary;
import com.HotelManagement.AirBnb.client.CatalogDtos.SummariesResponse;
import com.HotelManagement.AirBnb.shared.exception.BadRequestException;
import com.HotelManagement.AirBnb.shared.exception.ConflictException;
import com.HotelManagement.AirBnb.shared.exception.ResourceNotFoundException;
import com.HotelManagement.AirBnb.shared.exception.ServiceUnavailableException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory stand-in for catalog-service's internal API with the same rules and messages as its
 * InventoryHoldService (which has its own tests against PostgreSQL): every room type sells 365 days ahead at its
 * price, holds are idempotent per key, release/reacquire behave the same. "unavailable" simulates an outage.
 */
public class FakeCatalog extends CatalogClient {

    record Hotel(long id, long ownerId, String name, String city, String state, boolean active, boolean deleted) {
    }

    record Room(long id, long hotelId, String type, int capacity, int total, BigDecimal price, boolean deleted) {
    }

    static final class Hold {
        final HoldRequest request;
        final BigDecimal amount;
        volatile String status = "HELD";
        volatile Long bookingId;

        Hold(HoldRequest request, BigDecimal amount) {
            this.request = request;
            this.amount = amount;
        }
    }

    private final AtomicLong ids = new AtomicLong(System.nanoTime() % 1_000_000_000L);
    final Map<Long, Hotel> hotels = new ConcurrentHashMap<>();
    final Map<Long, Room> rooms = new ConcurrentHashMap<>();
    final Map<String, Integer> booked = new ConcurrentHashMap<>();
    final Map<UUID, Hold> holds = new ConcurrentHashMap<>();
    volatile boolean unavailable;
    volatile boolean releaseUnavailable;

    public FakeCatalog() {
        super(RestClient.create());
    }

    // ---------------------------------------------------------------- fixture

    long addHotel(long ownerId) {
        long id = ids.incrementAndGet();
        hotels.put(id, new Hotel(id, ownerId, "Hotel " + id, "Alappuzha", "Kerala", true, false));
        return id;
    }

    long addRoom(long hotelId, int total) {
        long id = ids.incrementAndGet();
        rooms.put(id, new Room(id, hotelId, "Deluxe " + id, 2, total, new BigDecimal("2500.00"), false));
        return id;
    }

    void deleteHotel(long hotelId) {
        hotels.computeIfPresent(hotelId, (k, h) -> new Hotel(h.id, h.ownerId, h.name, h.city, h.state, false, true));
        rooms.replaceAll((k, r) -> r.hotelId == hotelId ? new Room(r.id, r.hotelId, r.type, r.capacity, r.total, r.price, true) : r);
    }

    int bookedOn(long roomId, LocalDate date) {
        return booked.getOrDefault(roomId + "@" + date, 0);
    }

    // ---------------------------------------------------------------- internal API

    @Override
    public synchronized HoldResponse hold(HoldRequest r) {
        check();
        Hold existing = holds.get(r.holdKey());
        if (existing != null) {
            return response(existing);
        }
        if (r.checkInDate().isBefore(LocalDate.now())) throw new BadRequestException("Check-in date can't be in the past");
        if (!r.checkOutDate().isAfter(r.checkInDate())) throw new BadRequestException("Check-out date must be after the check-in date");
        Hotel hotel = hotels.get(r.hotelId());
        if (hotel == null || hotel.deleted) throw new ResourceNotFoundException("Hotel not found");
        if (!hotel.active) throw new ConflictException("This hotel is not accepting bookings right now");
        Room room = rooms.get(r.roomId());
        if (room == null || room.hotelId != hotel.id || room.deleted) throw new ResourceNotFoundException("Room type not found");
        if (r.guestCount() > room.capacity * r.roomsCount()) {
            throw new BadRequestException("Too many guests: " + r.roomsCount() + " room(s) of this type hold at most "
                    + room.capacity * r.roomsCount() + " guests");
        }
        if (r.checkOutDate().isAfter(LocalDate.now().plusDays(365))) {
            throw new ConflictException("This room type is not available for the selected dates");
        }
        for (LocalDate d = r.checkInDate(); d.isBefore(r.checkOutDate()); d = d.plusDays(1)) {
            if (room.total - bookedOn(room.id, d) < r.roomsCount()) throw new ConflictException("Not enough rooms available on " + d);
        }
        take(r, r.roomsCount());
        long nights = ChronoUnit.DAYS.between(r.checkInDate(), r.checkOutDate());
        Hold hold = new Hold(r, room.price.multiply(BigDecimal.valueOf(nights * r.roomsCount())));
        holds.put(r.holdKey(), hold);
        return response(hold);
    }

    @Override
    public synchronized void attach(UUID holdKey, Long bookingId) {
        check();
        Hold hold = holds.get(holdKey);
        if (hold == null) throw new ResourceNotFoundException("Hold " + holdKey + " not found");
        hold.bookingId = bookingId;
    }

    @Override
    public synchronized String release(UUID holdKey) {
        check();
        if (releaseUnavailable) throw new ServiceUnavailableException("catalog down");
        Hold hold = holds.get(holdKey);
        if (hold == null) return "NOT_FOUND";
        if ("HELD".equals(hold.status)) {
            take(hold.request, -hold.request.roomsCount());
            hold.status = "RELEASED";
        }
        return "RELEASED";
    }

    @Override
    public synchronized boolean reacquire(UUID holdKey) {
        check();
        Hold hold = holds.get(holdKey);
        if (hold == null) throw new ResourceNotFoundException("Hold " + holdKey + " not found");
        if ("HELD".equals(hold.status)) return true;
        Room room = rooms.get(hold.request.roomId());
        Hotel hotel = hotels.get(hold.request.hotelId());
        if (room == null || room.deleted || hotel == null || hotel.deleted) return false;
        for (LocalDate d = hold.request.checkInDate(); d.isBefore(hold.request.checkOutDate()); d = d.plusDays(1)) {
            if (room.total - bookedOn(room.id, d) < hold.request.roomsCount()) return false;
        }
        take(hold.request, hold.request.roomsCount());
        hold.status = "HELD";
        return true;
    }

    @Override
    public List<HoldResponse> unattached(int olderThanMinutes) {
        check();
        return holds.values().stream().filter(h -> h.bookingId == null && "HELD".equals(h.status)).map(this::response).toList();
    }

    @Override
    public SummariesResponse summaries(Collection<Long> hotelIds, Collection<Long> roomIds) {
        check();
        List<HotelSummary> hs = new ArrayList<>();
        for (Long id : hotelIds) {
            Hotel h = hotels.get(id);
            if (h != null) {
                hs.add(new HotelSummary(h.id, h.name, h.city, h.state, h.ownerId, h.active, h.deleted,
                        h.active && !h.deleted ? "/api/v1/hotels/" + h.id + "/photos/1" : null));
            }
        }
        List<RoomSummary> rs = new ArrayList<>();
        for (Long id : roomIds) {
            Room r = rooms.get(id);
            if (r != null) rs.add(new RoomSummary(r.id, r.hotelId, r.type, r.capacity, r.deleted));
        }
        return new SummariesResponse(hs, rs);
    }

    @Override
    public HotelCounts hotelCounts(Long ownerId) {
        check();
        long total = hotels.values().stream().filter(h -> h.ownerId == ownerId && !h.deleted).count();
        long active = hotels.values().stream().filter(h -> h.ownerId == ownerId && !h.deleted && h.active).count();
        return new HotelCounts(total, active);
    }

    private void check() {
        if (unavailable) throw new ServiceUnavailableException("Hotel information is temporarily unavailable. Please try again in a moment.");
    }

    private void take(HoldRequest r, int rooms) {
        for (LocalDate d = r.checkInDate(); d.isBefore(r.checkOutDate()); d = d.plusDays(1)) {
            booked.merge(r.roomId() + "@" + d, rooms, Integer::sum);
        }
    }

    private HoldResponse response(Hold h) {
        Hotel hotel = hotels.get(h.request.hotelId());
        Room room = rooms.get(h.request.roomId());
        return new HoldResponse(h.request.holdKey(), h.status, h.bookingId, hotel.id, hotel.ownerId, hotel.name,
                hotel.city, hotel.state, room.id, room.type, room.capacity, h.request.checkInDate(),
                h.request.checkOutDate(), h.request.roomsCount(), h.amount);
    }
}
