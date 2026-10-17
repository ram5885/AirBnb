package com.HotelManagement.AirBnb.hotel.hold;

import com.HotelManagement.AirBnb.shared.logging.AuditLogger;
import com.HotelManagement.AirBnb.hotel.entity.Hotel;
import com.HotelManagement.AirBnb.hotel.entity.Inventory;
import com.HotelManagement.AirBnb.hotel.entity.Room;
import com.HotelManagement.AirBnb.hotel.repository.HotelRepository;
import com.HotelManagement.AirBnb.hotel.repository.InventoryRepository;
import com.HotelManagement.AirBnb.hotel.repository.RoomRepository;
import com.HotelManagement.AirBnb.shared.exception.BadRequestException;
import com.HotelManagement.AirBnb.shared.exception.ConflictException;
import com.HotelManagement.AirBnb.shared.exception.ResourceNotFoundException;
import com.HotelManagement.AirBnb.search.service.StayRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Takes, releases and re-takes rooms in the date-wise inventory for bookings (the inventory half of the
 * monolith's BookingService / BookingInventory). Locks are taken in the original fixed order - hotel FOR SHARE,
 * room FOR SHARE, inventory rows FOR UPDATE by date - so concurrent holds serialize per date and can't oversell;
 * the inventory CHECK constraint (0 <= booked_count <= total_count) is the final guard. Every operation is
 * idempotent per hold key, so booking-service can safely retry.
 */
@Service
public class InventoryHoldService {

    private static final Logger log = LoggerFactory.getLogger(InventoryHoldService.class);

    private final InventoryHoldRepository holdRepository;
    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final InventoryRepository inventoryRepository;

    public InventoryHoldService(InventoryHoldRepository holdRepository, HotelRepository hotelRepository,
                                RoomRepository roomRepository, InventoryRepository inventoryRepository) {
        this.holdRepository = holdRepository;
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
        this.inventoryRepository = inventoryRepository;
    }

    /** Reserves roomsCount rooms for every night of the stay, or returns the existing hold for this key. */
    @Transactional
    public HoldResponse hold(HoldRequest request) {
        var existing = holdRepository.findByHoldKey(request.holdKey());
        if (existing.isPresent()) {
            log.info("Hold {} already exists (retried request); returning it unchanged", request.holdKey());
            return toResponse(existing.get());
        }
        LocalDate checkIn = request.checkInDate();
        LocalDate checkOut = request.checkOutDate();
        if (checkIn.isBefore(LocalDate.now())) {
            throw new BadRequestException("Check-in date can't be in the past");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new BadRequestException("Check-out date must be after the check-in date");
        }
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights > StayRules.MAX_NIGHTS) {
            throw new BadRequestException("A booking can be at most " + StayRules.MAX_NIGHTS + " nights");
        }

        Hotel hotel = hotelRepository.findByIdForShare(request.hotelId())
                .filter(h -> h.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found"));
        if (!Boolean.TRUE.equals(hotel.getActive())) {
            throw new ConflictException("This hotel is not accepting bookings right now");
        }
        Room room = roomRepository.findByIdForShare(request.roomId(), hotel.getHotelId())
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Room type not found"));
        int rooms = request.roomsCount();
        if (request.guestCount() > room.getCapacity() * rooms) {
            throw new BadRequestException("Too many guests: " + rooms + " room(s) of this type hold at most "
                    + room.getCapacity() * rooms + " guests");
        }

        List<Inventory> nightsInventory = inventoryRepository.findRangeForUpdate(room.getId(), checkIn, checkOut);
        if (nightsInventory.size() != nights) {
            throw new ConflictException("This room type is not available for the selected dates");
        }
        for (Inventory inv : nightsInventory) {
            if (Boolean.TRUE.equals(inv.getClosed())) {
                throw new ConflictException("This room type is not available on " + inv.getDate());
            }
            if (inv.getTotalCount() - inv.getBookedCount() < rooms) {
                log.info("Hold refused: room type {} has {} free room(s) on {}, {} requested", room.getId(),
                        inv.getTotalCount() - inv.getBookedCount(), inv.getDate(), rooms);
                throw new ConflictException("Not enough rooms available on " + inv.getDate());
            }
        }
        nightsInventory.forEach(inv -> inv.setBookedCount(inv.getBookedCount() + rooms));
        // Server-side price: each night's inventory price (base x surge) for every room. Never from the client.
        BigDecimal amount = nightsInventory.stream().map(Inventory::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(BigDecimal.valueOf(rooms));

        InventoryHold hold = new InventoryHold();
        hold.setHoldKey(request.holdKey());
        hold.setHotelId(hotel.getHotelId());
        hold.setRoomId(room.getId());
        hold.setCheckInDate(checkIn);
        hold.setCheckOutDate(checkOut);
        hold.setRoomsCount(rooms);
        hold.setAmount(amount);
        hold.setStatus(HoldStatus.HELD);
        holdRepository.saveAndFlush(hold);
        log.info("Held {} room(s) of room type {} in hotel {} for {}..{} (hold {}, amount {})", rooms, room.getId(),
                hotel.getHotelId(), checkIn, checkOut, request.holdKey(), amount);
        AuditLogger.success("ROOMS_HELD", "holdKey", request.holdKey(), "hotelId", hotel.getHotelId(), "roomId", room.getId(),
                "checkIn", checkIn, "checkOut", checkOut, "rooms", rooms, "amount", amount);
        return toResponse(hold, hotel, room);
    }

    /** Links the hold to the booking that now owns it (after booking-service committed the booking). */
    @Transactional
    public HoldResponse attach(UUID holdKey, Long bookingId) {
        InventoryHold hold = requireForUpdate(holdKey);
        if (hold.getBookingId() != null && !hold.getBookingId().equals(bookingId)) {
            throw new ConflictException("This hold belongs to another booking");
        }
        hold.setBookingId(bookingId);
        holdRepository.flush();
        log.debug("Hold {} attached to booking {}", holdKey, bookingId);
        return toResponse(hold);
    }

    /** Gives the rooms back. Idempotent: releasing a released (or unknown) hold changes nothing. */
    @Transactional
    public HoldStatus release(UUID holdKey) {
        InventoryHold hold = holdRepository.findByHoldKeyForUpdate(holdKey).orElse(null);
        if (hold == null) {
            return null;
        }
        if (hold.getStatus() == HoldStatus.RELEASED) {
            return HoldStatus.RELEASED;
        }
        for (Inventory inv : lockNights(hold)) {
            inv.setBookedCount(Math.max(0, inv.getBookedCount() - hold.getRoomsCount()));
        }
        hold.setStatus(HoldStatus.RELEASED);
        log.info("Released hold {} (booking {})", holdKey, hold.getBookingId());
        AuditLogger.success("ROOMS_RELEASED", "holdKey", holdKey, "bookingId", hold.getBookingId(),
                "roomId", hold.getRoomId(), "rooms", hold.getRoomsCount());
        return HoldStatus.RELEASED;
    }

    /**
     * Re-takes the rooms of a released hold (late payment after the reservation expired). Returns true when the
     * rooms are held afterwards (also when they never were released), false when the stay is no longer bookable.
     */
    @Transactional
    public boolean reacquire(UUID holdKey) {
        InventoryHold hold = requireForUpdate(holdKey);
        if (hold.getStatus() == HoldStatus.HELD) {
            return true;
        }
        if (hold.getCheckInDate().isBefore(LocalDate.now())) {
            return false;
        }
        boolean hotelLive = hotelRepository.findHotelById(hold.getHotelId()).map(h -> h.getDeletedAt() == null).orElse(false);
        boolean roomLive = roomRepository.findLiveInHotel(hold.getRoomId(), hold.getHotelId()).isPresent();
        if (!hotelLive || !roomLive) {
            return false;
        }
        List<Inventory> nights = lockNights(hold);
        long expected = ChronoUnit.DAYS.between(hold.getCheckInDate(), hold.getCheckOutDate());
        if (nights.size() != expected) {
            return false;
        }
        for (Inventory inv : nights) {
            if (Boolean.TRUE.equals(inv.getClosed()) || inv.getTotalCount() - inv.getBookedCount() < hold.getRoomsCount()) {
                return false;
            }
        }
        nights.forEach(inv -> inv.setBookedCount(inv.getBookedCount() + hold.getRoomsCount()));
        hold.setStatus(HoldStatus.HELD);
        log.info("Re-acquired hold {} (booking {})", holdKey, hold.getBookingId());
        AuditLogger.success("ROOMS_REACQUIRED", "holdKey", holdKey, "bookingId", hold.getBookingId(),
                "roomId", hold.getRoomId(), "rooms", hold.getRoomsCount());
        return true;
    }

    /** HELD holds created before the cutoff that no booking has claimed (for booking-service's reconciliation). */
    @Transactional(readOnly = true)
    public List<HoldResponse> unattached(LocalDateTime cutoff) {
        return holdRepository.findUnattachedBefore(cutoff).stream().map(this::toResponse).toList();
    }

    private InventoryHold requireForUpdate(UUID holdKey) {
        return holdRepository.findByHoldKeyForUpdate(holdKey)
                .orElseThrow(() -> new ResourceNotFoundException("Hold " + holdKey + " not found"));
    }

    private List<Inventory> lockNights(InventoryHold hold) {
        return inventoryRepository.findRangeForUpdate(hold.getRoomId(), hold.getCheckInDate(), hold.getCheckOutDate());
    }

    private HoldResponse toResponse(InventoryHold hold) {
        Hotel hotel = hotelRepository.findHotelById(hold.getHotelId()).orElse(null);
        Room room = roomRepository.findRoomById(hold.getRoomId()).orElse(null);
        return toResponse(hold, hotel, room);
    }

    private static HoldResponse toResponse(InventoryHold hold, Hotel hotel, Room room) {
        return new HoldResponse(hold.getHoldKey(), hold.getStatus(), hold.getBookingId(), hold.getHotelId(),
                hotel == null ? null : hotel.getOwnerId(), hotel == null ? null : hotel.getName(),
                hotel == null ? null : hotel.getLocation().getCity(), hotel == null ? null : hotel.getLocation().getState(),
                hold.getRoomId(), room == null ? null : room.getType(), room == null ? null : room.getCapacity(),
                hold.getCheckInDate(), hold.getCheckOutDate(), hold.getRoomsCount(),
                Objects.requireNonNullElse(hold.getAmount(), BigDecimal.ZERO));
    }
}
