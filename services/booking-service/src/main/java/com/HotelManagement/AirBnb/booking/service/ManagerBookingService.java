package com.HotelManagement.AirBnb.booking.service;

import com.HotelManagement.AirBnb.shared.logging.AuditLogger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.HotelManagement.AirBnb.booking.dto.BookingDetailResponse;
import com.HotelManagement.AirBnb.booking.dto.BookingResponse;
import com.HotelManagement.AirBnb.booking.entity.Booking;
import com.HotelManagement.AirBnb.booking.entity.BookingStatus;
import com.HotelManagement.AirBnb.booking.repository.BookingRepository;
import com.HotelManagement.AirBnb.payment.entity.PaymentMethod;
import com.HotelManagement.AirBnb.payment.entity.PaymentStatus;
import com.HotelManagement.AirBnb.shared.dto.PageResponse;
import com.HotelManagement.AirBnb.shared.exception.BadRequestException;
import com.HotelManagement.AirBnb.shared.exception.ConflictException;
import com.HotelManagement.AirBnb.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Booking management for hotel managers: list/filter, view, accept and cancel bookings
 * of hotels they own. Status transitions:
 * RESERVED -> ACCEPTED (accept), RESERVED|ACCEPTED|CONFIRMED -> CANCELLED (cancel: releases inventory and
 * refunds a paid booking in full). Paid bookings are confirmed by payment, not by the manager.
 */
@Service
public class ManagerBookingService {

    private static final Logger log = LoggerFactory.getLogger(ManagerBookingService.class);

    private final BookingRepository bookingRepository;
    private final BookingCancellationService cancellationService;
    private final BookingViews views;

    public ManagerBookingService(BookingRepository bookingRepository, BookingCancellationService cancellationService,
                                 BookingViews views) {
        this.bookingRepository = bookingRepository;
        this.cancellationService = cancellationService;
        this.views = views;
    }

    /** Bookings of the owner's hotels; startDate/endDate select stays overlapping that window. */
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> list(Long ownerId, Long hotelId, BookingStatus status,
                                              LocalDate startDate, LocalDate endDate, Long bookingId,
                                              int page, int size) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BadRequestException("End date must be on or after the start date");
        }
        Page<Booking> bookings = bookingRepository.findOwnerPage(ownerId, hotelId,
                status == null ? null : status.name(), startDate, endDate, bookingId, PageRequest.of(page, size));
        List<Long> ids = bookings.getContent().stream().map(Booking::getBookingId).toList();
        Map<Long, Long> guestCounts = ids.isEmpty() ? Map.of()
                : bookingRepository.countGuests(ids).stream().collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));

        BookingViews.Catalog catalog = views.forBookings(bookings.getContent());

        return PageResponse.of(bookings, b -> new BookingResponse(b.getBookingId(), b.getHotelId(),
                catalog.hotel(b).name(), b.getRoomId(), catalog.room(b).type(), b.getCheckInDate(),
                b.getCheckOutDate(), nights(b), b.getRoomsCount(), guestCounts.getOrDefault(b.getBookingId(), 0L),
                b.getBookingStatus(), b.getAmount(), b.getPayment() == null ? null : b.getPayment().getPaymentStatus(),
                GuestBookingMapper.methodOf(b.getPayment()), b.getUserName(), b.getCreatedAt(), b.getUpdatedAt()));
    }

    @Transactional(readOnly = true)
    public BookingDetailResponse get(Long ownerId, Long bookingId) {
        return toDetail(bookingRepository.findForOwner(bookingId, ownerId).orElseThrow(() -> notFound(bookingId)));
    }

    /** RESERVED -> ACCEPTED. Inventory was already reserved when the booking was created. */
    @Transactional
    public BookingDetailResponse accept(Long ownerId, Long bookingId) {
        Booking booking = bookingRepository.findForOwnerForUpdate(bookingId, ownerId).orElseThrow(() -> notFound(bookingId));
        if (booking.getBookingStatus() != BookingStatus.RESERVED) {
            throw new ConflictException("Only reserved bookings can be accepted. This booking is "
                    + booking.getBookingStatus() + ".");
        }
        BookingViews.Catalog live = views.live(booking); // the hotel's current state decides; 503 if unknown
        if (live.hotel(booking).deleted() || live.room(booking).deleted()) {
            throw new ConflictException("The hotel or room type of this booking has been deleted");
        }
        if (booking.getCheckInDate().isBefore(LocalDate.now())) {
            throw new ConflictException("The check-in date of this booking has already passed");
        }
        booking.setBookingStatus(BookingStatus.ACCEPTED);
        bookingRepository.flush();
        log.info("Manager {} accepted booking {}", ownerId, bookingId);
        AuditLogger.success("BOOKING_ACCEPTED", "bookingId", bookingId, "hotelId", booking.getHotelId());
        return get(ownerId, bookingId);
    }

    /**
     * Cancels a booking that still holds inventory, up to its check-out date: releases its rooms and,
     * if it was paid, refunds it in full through the payment gateway (see BookingCancellationService).
     * The booking row lock plus the status check guarantee this happens exactly once.
     */
    @Transactional
    public BookingDetailResponse cancel(Long ownerId, Long bookingId) {
        Booking booking = bookingRepository.findForOwnerForUpdate(bookingId, ownerId).orElseThrow(() -> notFound(bookingId));
        if (!BookingService.INVENTORY_HOLDING_STATUSES.contains(booking.getBookingStatus())) {
            throw new ConflictException("This booking is already " + booking.getBookingStatus() + ".");
        }
        if (!booking.getCheckOutDate().isAfter(LocalDate.now())) {
            throw new ConflictException("Completed stays can't be cancelled");
        }
        cancellationService.cancel(booking, "hotel manager");
        bookingRepository.flush();
        return get(ownerId, bookingId);
    }

    /** Records that the guest paid a pay-at-hotel booking at the front desk. */
    @Transactional
    public BookingDetailResponse markPaymentCollected(Long ownerId, Long bookingId) {
        Booking booking = bookingRepository.findForOwnerForUpdate(bookingId, ownerId).orElseThrow(() -> notFound(bookingId));
        if (GuestBookingMapper.methodOf(booking.getPayment()) != PaymentMethod.PAY_AT_HOTEL) {
            throw new ConflictException("Only pay-at-hotel bookings can be marked as paid at the hotel.");
        }
        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            throw new ConflictException("This booking is " + booking.getBookingStatus() + ".");
        }
        if (booking.getPayment().getPaymentStatus() != PaymentStatus.PENDING) {
            throw new ConflictException("Payment for this booking is already " + booking.getPayment().getPaymentStatus() + ".");
        }
        booking.getPayment().setPaymentStatus(PaymentStatus.CONFIRMED);
        bookingRepository.flush();
        log.info("Manager {} recorded the pay-at-hotel payment of booking {}", ownerId, bookingId);
        AuditLogger.success("PAYMENT_COLLECTED_AT_HOTEL", "bookingId", bookingId, "amount", booking.getAmount());
        return get(ownerId, bookingId);
    }

    private BookingDetailResponse toDetail(Booking b) {
        List<BookingDetailResponse.GuestInfo> guests = b.getGuests() == null ? List.of()
                : b.getGuests().stream()
                .sorted(Comparator.comparing(g -> g.getId()))
                .map(g -> new BookingDetailResponse.GuestInfo(g.getName(), g.getGender(), g.getAge()))
                .toList();
        BookingViews.Catalog catalog = views.forBookings(List.of(b));
        BookingViews.HotelFacts hotel = catalog.hotel(b);
        return new BookingDetailResponse(b.getBookingId(), b.getHotelId(), hotel.name(),
                hotel.city(), hotel.deleted(), b.getRoomId(), catalog.room(b).type(),
                b.getCheckInDate(), b.getCheckOutDate(), nights(b), b.getRoomsCount(), b.getBookingStatus(),
                b.getUserName(), b.getUserEmail(), guests, b.getAmount(),
                b.getPayment() == null ? null : b.getPayment().getPaymentStatus(), GuestBookingMapper.methodOf(b.getPayment()),
                b.getPayment() == null ? null : b.getPayment().getAmount(),
                b.getCreatedAt(), b.getUpdatedAt());
    }

    private static long nights(Booking b) {
        return ChronoUnit.DAYS.between(b.getCheckInDate(), b.getCheckOutDate());
    }

    private static ResourceNotFoundException notFound(Long bookingId) {
        return new ResourceNotFoundException("Booking " + bookingId + " not found");
    }
}
