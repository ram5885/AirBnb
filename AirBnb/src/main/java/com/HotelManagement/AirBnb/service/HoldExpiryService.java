package com.HotelManagement.AirBnb.service;

import com.HotelManagement.AirBnb.entity.Booking;
import com.HotelManagement.AirBnb.entity.Payment;
import com.HotelManagement.AirBnb.entity.enums.BookingStatus;
import com.HotelManagement.AirBnb.entity.enums.PaymentStatus;
import com.HotelManagement.AirBnb.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * Expires unpaid RESERVED bookings after app.booking.hold-minutes and releases their rooms
 * (the diagram's "resetBookings" job, every minute). Each booking is handled in its own transaction
 * under its row lock, re-checking the status, so a payment being verified at the same moment wins or
 * loses cleanly and inventory is released exactly once. ACCEPTED bookings (approved by a manager) don't expire.
 */
@Service
public class HoldExpiryService {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryService.class);

    private final BookingRepository bookingRepository;
    private final BookingInventory bookingInventory;
    private final GuestBookingMapper mapper;
    private final TransactionTemplate tx;

    public HoldExpiryService(BookingRepository bookingRepository, BookingInventory bookingInventory,
                             GuestBookingMapper mapper, PlatformTransactionManager transactionManager) {
        this.bookingRepository = bookingRepository;
        this.bookingInventory = bookingInventory;
        this.mapper = mapper;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.booking.expiry-check-ms:60000}", initialDelayString = "${app.booking.expiry-check-ms:60000}")
    public void expireStaleHolds() {
        expireHoldsCreatedBefore(LocalDateTime.now().minusMinutes(mapper.holdMinutes()));
    }

    /** Expires RESERVED bookings created before the cutoff; returns how many were expired. */
    public int expireHoldsCreatedBefore(LocalDateTime cutoff) {
        int expired = 0;
        for (Long id : bookingRepository.findStaleHoldIds(cutoff)) {
            try {
                if (Boolean.TRUE.equals(tx.execute(s -> expireOne(id, cutoff)))) {
                    expired++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not expire booking {} (will retry next run): {}", id, e.getMessage());
            }
        }
        if (expired > 0) {
            log.info("Expired {} unpaid reservation(s) and released their rooms", expired);
        }
        return expired;
    }

    private boolean expireOne(Long bookingId, LocalDateTime cutoff) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null || booking.getBookingStatus() != BookingStatus.RESERVED
                || !booking.getCreatedAt().isBefore(cutoff) || mapper.isPaid(booking)) {
            return false; // paid, accepted or cancelled since we looked
        }
        bookingInventory.release(booking);
        booking.setBookingStatus(BookingStatus.EXPIRED);
        Payment payment = booking.getPayment();
        if (payment != null && (payment.getPaymentStatus() == PaymentStatus.PENDING
                || payment.getPaymentStatus() == PaymentStatus.FAILED)) {
            payment.setPaymentStatus(PaymentStatus.CANCELLED);
        }
        return true;
    }
}
