package com.HotelManagement.AirBnb.booking.service;

import com.HotelManagement.AirBnb.auth.entity.User;
import com.HotelManagement.AirBnb.booking.dto.GuestBookingResponse;
import com.HotelManagement.AirBnb.booking.entity.Booking;
import com.HotelManagement.AirBnb.booking.entity.BookingStatus;
import com.HotelManagement.AirBnb.booking.repository.BookingRepository;
import com.HotelManagement.AirBnb.shared.dto.PageResponse;
import com.HotelManagement.AirBnb.shared.exception.BadRequestException;
import com.HotelManagement.AirBnb.shared.exception.ConflictException;
import com.HotelManagement.AirBnb.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** "My Bookings": the authenticated user's own bookings. Other users' bookings are indistinguishable from missing ones. */
@Service
public class GuestBookingService {

    public enum Scope { ALL, UPCOMING, PENDING, PAST, CANCELLED }

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final BookingCancellationService cancellationService;
    private final GuestBookingMapper mapper;

    public GuestBookingService(BookingRepository bookingRepository, BookingService bookingService,
                               BookingCancellationService cancellationService, GuestBookingMapper mapper) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.cancellationService = cancellationService;
        this.mapper = mapper;
    }

    public static Scope parseScope(String value) {
        if (value == null || value.isBlank()) {
            return Scope.ALL;
        }
        try {
            return Scope.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("scope must be one of all, upcoming, pending, past or cancelled");
        }
    }

    /**
     * upcoming = confirmed stays that haven't ended; pending = reserved/accepted and awaiting payment;
     * past = confirmed stays that have ended; cancelled = cancelled or expired.
     */
    @Transactional(readOnly = true)
    public PageResponse<GuestBookingResponse> list(User user, Scope scope, int page, int size) {
        LocalDate today = LocalDate.now();
        List<String> statuses = switch (scope) {
            case UPCOMING, PAST -> List.of(BookingStatus.CONFIRMED.name());
            case PENDING -> List.of(BookingStatus.RESERVED.name(), BookingStatus.ACCEPTED.name());
            case CANCELLED -> List.of(BookingStatus.CANCELLED.name(), BookingStatus.EXPIRED.name());
            case ALL -> java.util.Arrays.stream(BookingStatus.values()).map(Enum::name).toList();
        };
        LocalDate checkoutAfter = scope == Scope.UPCOMING ? today : null;      // stay not over yet
        LocalDate checkoutOnOrBefore = scope == Scope.PAST ? today : null;     // stay finished
        boolean byCheckIn = scope == Scope.UPCOMING || scope == Scope.PENDING; // soonest first; else newest first
        Page<Booking> bookings = bookingRepository.findUserPage(user.getId(), statuses, checkoutAfter,
                checkoutOnOrBefore, byCheckIn, PageRequest.of(page, size));

        List<Long> ids = bookings.getContent().stream().map(Booking::getBookingId).toList();
        Map<Long, Long> guestCounts = ids.isEmpty() ? Map.of()
                : bookingRepository.countGuests(ids).stream().collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        Map<Long, String> covers = mapper.coverPhotos(bookings.getContent().stream().map(Booking::getHotel).toList());
        return PageResponse.of(bookings, b -> mapper.toResponse(b, covers.get(b.getHotel().getHotelId()),
                guestCounts.getOrDefault(b.getBookingId(), 0L), false));
    }

    @Transactional(readOnly = true)
    public GuestBookingResponse get(User user, Long bookingId) {
        return mapper.toDetail(requireOwn(user, bookingId));
    }

    /** Guests can cancel until the day before check-in; paid bookings are refunded in full. */
    @Transactional
    public GuestBookingResponse cancel(User user, Long bookingId) {
        Booking booking = bookingRepository.findForUserForUpdate(bookingId, user.getId()).orElseThrow(() -> notFound(bookingId));
        if (!BookingService.INVENTORY_HOLDING_STATUSES.contains(booking.getBookingStatus())) {
            throw new ConflictException("This booking is already " + booking.getBookingStatus() + ".");
        }
        if (!booking.getCheckInDate().isAfter(LocalDate.now())) {
            throw new ConflictException("Bookings can be cancelled until the day before check-in.");
        }
        cancellationService.cancel(booking, "guest");
        bookingRepository.flush();
        return mapper.toDetail(requireOwn(user, bookingId));
    }

    /** Replace the guest list of an active booking before check-in. */
    @Transactional
    public GuestBookingResponse updateGuests(User user, Long bookingId, List<Long> guestIds) {
        Booking booking = bookingRepository.findForUserForUpdate(bookingId, user.getId()).orElseThrow(() -> notFound(bookingId));
        if (!BookingService.INVENTORY_HOLDING_STATUSES.contains(booking.getBookingStatus())
                || !booking.getCheckInDate().isAfter(LocalDate.now())) {
            throw new ConflictException("Guests can only be changed on active bookings before check-in.");
        }
        booking.setGuests(bookingService.resolveGuests(user, guestIds, booking.getRoom().getCapacity(),
                booking.getRoomsCount()));
        bookingRepository.flush();
        return mapper.toDetail(requireOwn(user, bookingId));
    }

    Booking requireOwn(User user, Long bookingId) {
        return bookingRepository.findForUser(bookingId, user.getId()).orElseThrow(() -> notFound(bookingId));
    }

    private static ResourceNotFoundException notFound(Long bookingId) {
        return new ResourceNotFoundException("Booking " + bookingId + " not found");
    }
}
