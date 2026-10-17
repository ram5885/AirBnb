package com.HotelManagement.AirBnb.booking.service;

import com.HotelManagement.AirBnb.shared.logging.AuditLogger;
import com.HotelManagement.AirBnb.booking.dto.CreateBookingRequest;
import com.HotelManagement.AirBnb.booking.dto.GuestBookingResponse;
import com.HotelManagement.AirBnb.booking.entity.Booking;
import com.HotelManagement.AirBnb.booking.entity.BookingStatus;
import com.HotelManagement.AirBnb.booking.entity.HoldStatus;
import com.HotelManagement.AirBnb.booking.repository.BookingRepository;
import com.HotelManagement.AirBnb.client.AuthClient;
import com.HotelManagement.AirBnb.client.CatalogClient;
import com.HotelManagement.AirBnb.client.CatalogDtos.HoldRequest;
import com.HotelManagement.AirBnb.client.CatalogDtos.HoldResponse;
import com.HotelManagement.AirBnb.guest.entity.Guest;
import com.HotelManagement.AirBnb.guest.repository.GuestRepository;
import com.HotelManagement.AirBnb.shared.exception.BadRequestException;
import com.HotelManagement.AirBnb.shared.exception.ServiceUnavailableException;
import com.HotelManagement.AirBnb.shared.exception.UnauthorizedException;
import com.HotelManagement.AirBnb.shared.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Guest booking creation (check availability, then reserve). Rooms are taken in catalog-service's inventory
 * (the authority on availability and price, under row locks there) with an idempotent hold, then the booking is
 * recorded here. If recording fails the hold is released; if this service stops in between, HoldMaintenanceJobs
 * releases the orphan hold. Availability and price are never trusted from the client.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

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
    private final GuestRepository guestRepository;
    private final CatalogClient catalogClient;
    private final AuthClient authClient;
    private final GuestBookingMapper mapper;
    private final TransactionTemplate tx;

    public BookingService(BookingRepository bookingRepository, GuestRepository guestRepository,
                          CatalogClient catalogClient, AuthClient authClient, GuestBookingMapper mapper,
                          PlatformTransactionManager transactionManager) {
        this.bookingRepository = bookingRepository;
        this.guestRepository = guestRepository;
        this.catalogClient = catalogClient;
        this.authClient = authClient;
        this.mapper = mapper;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * The user's own saved guests for a booking. Unknown ids or other users' guests are rejected,
     * and the party can't exceed what the booked rooms hold (capacity x rooms).
     */
    public Set<Guest> resolveGuests(CurrentUser user, List<Long> guestIds, int capacity, int rooms) {
        Set<Guest> guests = ownGuests(user, guestIds);
        if (guests.size() > capacity * rooms) {
            throw new BadRequestException("Too many guests: " + rooms + " room(s) of this type hold at most "
                    + capacity * rooms + " guests");
        }
        return guests;
    }

    private Set<Guest> ownGuests(CurrentUser user, List<Long> guestIds) {
        if (guestIds == null || guestIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<Long> unique = new HashSet<>(guestIds);
        List<Guest> guests = guestRepository.findByIdsForUser(unique, user.getId());
        if (guests.size() != unique.size()) {
            throw new BadRequestException("Choose guests from your saved guest list");
        }
        return new LinkedHashSet<>(guests);
    }

    /**
     * Reserves {@code roomsCount} rooms for every night of the stay. catalog-service checks the hotel (active, not
     * deleted), the room type, the party size against its capacity and every night's inventory, all under row
     * locks, and computes the amount; its 400/404/409 answers are passed through unchanged.
     */
    public GuestBookingResponse create(CurrentUser user, CreateBookingRequest request) {
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
        int guestCount = ownGuests(user, request.guestIds()).size();
        AuthClient.UserView profile = authClient.findUser(user.getId())
                .orElseThrow(() -> new UnauthorizedException("Your session has expired. Please log in again."));

        UUID holdKey = UUID.randomUUID();
        HoldResponse hold;
        try {
            hold = catalogClient.hold(new HoldRequest(holdKey, request.hotelId(), request.roomId(), checkIn, checkOut,
                    request.roomsCount(), guestCount));
        } catch (ServiceUnavailableException e) {
            log.warn("Holding rooms failed (catalog unavailable) for hold {}: {}", holdKey, e.getMessage());
            // Unknown outcome (e.g. timeout after catalog took the rooms): give them back if they were taken.
            releaseQuietly(holdKey);
            throw e;
        }

        Booking booking;
        try {
            booking = tx.execute(s -> {
                Booking b = new Booking();
                b.setHotelId(hold.hotelId());
                b.setRoomId(hold.roomId());
                b.setUserId(user.getId());
                b.setHotelOwnerId(hold.hotelOwnerId());
                b.setRoomsCount(hold.roomsCount());
                b.setCheckInDate(checkIn);
                b.setCheckOutDate(checkOut);
                b.setBookingStatus(BookingStatus.RESERVED);
                // Server-side price from catalog's inventory (each night's base x surge, every room).
                b.setAmount(hold.amount());
                b.setGuests(ownGuests(user, request.guestIds()));
                b.setHoldKey(holdKey);
                b.setHoldStatus(HoldStatus.HELD);
                b.setHotelName(hold.hotelName());
                b.setHotelCity(hold.city());
                b.setHotelState(hold.state());
                b.setRoomType(hold.roomType());
                b.setRoomCapacity(hold.roomCapacity());
                b.setUserName(profile.name());
                b.setUserEmail(profile.email());
                return bookingRepository.saveAndFlush(b);
            });
        } catch (RuntimeException e) {
            log.error("Saving booking failed after rooms were held (hold {}); releasing them: {}", holdKey, e.toString());
            releaseQuietly(holdKey); // compensation: the booking wasn't recorded, so nothing may keep the rooms
            throw e;
        }

        log.info("Booking {} reserved for user {}: hotel {} room type {} {}..{} x{} amount {}", booking.getBookingId(),
                user.getId(), hold.hotelId(), hold.roomId(), checkIn, checkOut, hold.roomsCount(), hold.amount());
        AuditLogger.success("BOOKING_CREATED", "bookingId", booking.getBookingId(), "hotelId", hold.hotelId(),
                "roomId", hold.roomId(), "checkIn", checkIn, "checkOut", checkOut, "rooms", hold.roomsCount(),
                "guests", guestCount, "amount", hold.amount(), "holdKey", holdKey);
        try {
            catalogClient.attach(holdKey, booking.getBookingId());
        } catch (RuntimeException e) {
            log.warn("Hold {} for booking {} not attached yet (reconciliation will do it): {}", holdKey,
                    booking.getBookingId(), e.getMessage());
        }
        return mapper.toDetail(booking);
    }

    private void releaseQuietly(UUID holdKey) {
        try {
            catalogClient.release(holdKey);
        } catch (RuntimeException e) {
            log.warn("Could not release hold {} (reconciliation will): {}", holdKey, e.getMessage());
        }
    }
}
