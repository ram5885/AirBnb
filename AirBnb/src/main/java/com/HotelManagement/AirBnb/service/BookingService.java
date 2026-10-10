package com.HotelManagement.AirBnb.service;

import com.HotelManagement.AirBnb.dto.booking.CreateBookingRequest;
import com.HotelManagement.AirBnb.dto.booking.GuestBookingResponse;
import com.HotelManagement.AirBnb.entity.Booking;
import com.HotelManagement.AirBnb.entity.Guest;
import com.HotelManagement.AirBnb.entity.Hotel;
import com.HotelManagement.AirBnb.entity.Inventory;
import com.HotelManagement.AirBnb.entity.Room;
import com.HotelManagement.AirBnb.entity.User;
import com.HotelManagement.AirBnb.entity.enums.BookingStatus;
import com.HotelManagement.AirBnb.exception.BadRequestException;
import com.HotelManagement.AirBnb.exception.ConflictException;
import com.HotelManagement.AirBnb.exception.ResourceNotFoundException;
import com.HotelManagement.AirBnb.repository.BookingRepository;
import com.HotelManagement.AirBnb.repository.GuestRepository;
import com.HotelManagement.AirBnb.repository.HotelRepository;
import com.HotelManagement.AirBnb.repository.InventoryRepository;
import com.HotelManagement.AirBnb.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Guest booking creation (step 4a/4b of the booking flow: check availability, then reserve).
 * Availability is enforced here with row locks, not trusted from the client.
 */
@Service
public class BookingService {

    /** Statuses whose rooms are still held in inventory. */
    public static final Set<BookingStatus> INVENTORY_HOLDING_STATUSES =
            EnumSet.of(BookingStatus.RESERVED, BookingStatus.ACCEPTED, BookingStatus.CONFIRMED);

    /** The same statuses as stored in the booking_status column, for native queries. */
    public static final List<String> INVENTORY_HOLDING_STATUS_NAMES =
            INVENTORY_HOLDING_STATUSES.stream().map(Enum::name).toList();

    /** Longest stay that can be booked (and searched). */
    public static final int MAX_NIGHTS = 30;

    /** Most rooms in one booking (and search); matches CreateBookingRequest.roomsCount. */
    public static final int MAX_ROOMS = 10;

    private final BookingRepository bookingRepository;
    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final InventoryRepository inventoryRepository;
    private final GuestRepository guestRepository;
    private final GuestBookingMapper mapper;

    public BookingService(BookingRepository bookingRepository, HotelRepository hotelRepository,
                          RoomRepository roomRepository, InventoryRepository inventoryRepository,
                          GuestRepository guestRepository, GuestBookingMapper mapper) {
        this.bookingRepository = bookingRepository;
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
        this.inventoryRepository = inventoryRepository;
        this.guestRepository = guestRepository;
        this.mapper = mapper;
    }

    /**
     * The user's own saved guests for a booking. Unknown ids or other users' guests are rejected,
     * and the party can't exceed what the booked rooms hold (capacity x rooms).
     */
    public Set<Guest> resolveGuests(User user, List<Long> guestIds, int capacity, int rooms) {
        if (guestIds == null || guestIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<Long> unique = new HashSet<>(guestIds);
        List<Guest> guests = guestRepository.findByIdsForUser(unique, user.getId());
        if (guests.size() != unique.size()) {
            throw new BadRequestException("Choose guests from your saved guest list");
        }
        if (guests.size() > capacity * rooms) {
            throw new BadRequestException("Too many guests: " + rooms + " room(s) of this type hold at most "
                    + capacity * rooms + " guests");
        }
        return new LinkedHashSet<>(guests);
    }

    /**
     * Reserves {@code roomsCount} rooms for every night of the stay. Locks are taken in a fixed
     * order (hotel FOR SHARE, room FOR SHARE, inventory rows FOR UPDATE by date), so concurrent
     * bookings serialize per date and can't oversell; the inventory check constraint is a final guard.
     */
    @Transactional
    public GuestBookingResponse create(User user, CreateBookingRequest request) {
        LocalDate checkIn = request.checkInDate();
        LocalDate checkOut = request.checkOutDate();
        if (checkIn.isBefore(LocalDate.now())) {
            throw new BadRequestException("Check-in date can't be in the past");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new BadRequestException("Check-out date must be after the check-in date");
        }
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights > MAX_NIGHTS) {
            throw new BadRequestException("A booking can be at most " + MAX_NIGHTS + " nights");
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
        Set<Guest> guests = resolveGuests(user, request.guestIds(), room.getCapacity(), request.roomsCount());

        List<Inventory> nightsInventory = inventoryRepository.findRangeForUpdate(room.getId(), checkIn, checkOut);
        if (nightsInventory.size() != nights) {
            throw new ConflictException("This room type is not available for the selected dates");
        }
        int rooms = request.roomsCount();
        for (Inventory inv : nightsInventory) {
            if (Boolean.TRUE.equals(inv.getClosed())) {
                throw new ConflictException("This room type is not available on " + inv.getDate());
            }
            if (inv.getTotalCount() - inv.getBookedCount() < rooms) {
                throw new ConflictException("Not enough rooms available on " + inv.getDate());
            }
        }
        nightsInventory.forEach(inv -> inv.setBookedCount(inv.getBookedCount() + rooms));
        // Server-side price: each night's inventory price (base x surge) for every room. Never from the client.
        BigDecimal amount = nightsInventory.stream().map(Inventory::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(BigDecimal.valueOf(rooms));

        Booking booking = new Booking();
        booking.setHotel(hotel);
        booking.setRoom(room);
        booking.setUser(user);
        booking.setRoomsCount(rooms);
        booking.setCheckInDate(checkIn);
        booking.setCheckOutDate(checkOut);
        booking.setBookingStatus(BookingStatus.RESERVED);
        booking.setAmount(amount);
        booking.setGuests(guests);
        bookingRepository.saveAndFlush(booking);

        return mapper.toDetail(booking);
    }
}
