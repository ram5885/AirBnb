package com.HotelManagement.AirBnb.booking.service;

import com.HotelManagement.AirBnb.booking.entity.Booking;
import com.HotelManagement.AirBnb.booking.entity.HoldStatus;
import com.HotelManagement.AirBnb.booking.repository.BookingRepository;
import com.HotelManagement.AirBnb.client.CatalogClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gives a cancelled/expired booking's rooms back to catalog-service. The booking is first durably marked
 * (status CANCELLED/EXPIRED, hold RELEASE_PENDING) in the caller's transaction; only after that commits is
 * catalog asked to release. If the call fails, HoldMaintenanceJobs retries it. So rooms can be blocked a little
 * longer than needed, but never handed out while a booking still holds them. Release is idempotent in catalog.
 */
@Component
public class HoldReleaser {

    private static final Logger log = LoggerFactory.getLogger(HoldReleaser.class);

    private final BookingRepository bookingRepository;
    private final CatalogClient catalogClient;
    private final TransactionTemplate tx;

    public HoldReleaser(BookingRepository bookingRepository, CatalogClient catalogClient,
                        PlatformTransactionManager transactionManager) {
        this.bookingRepository = bookingRepository;
        this.catalogClient = catalogClient;
        this.tx = new TransactionTemplate(transactionManager);
        // Runs from afterCommit callbacks, where the finished transaction's resources are still bound: it must
        // have a transaction of its own, or its changes would join the already-committed one and be lost.
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Marks the hold RELEASE_PENDING and releases it once the current transaction has committed. */
    public void releaseAfterCommit(Booking booking) {
        booking.setHoldStatus(HoldStatus.RELEASE_PENDING);
        Long bookingId = booking.getBookingId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    releaseNow(bookingId);
                }
            });
        } else {
            releaseNow(bookingId);
        }
    }

    /**
     * Releases a RELEASE_PENDING hold under the booking's row lock (so a late payment re-confirming the booking
     * at the same moment wins or loses cleanly). Returns true when catalog confirmed the release.
     */
    public boolean releaseNow(Long bookingId) {
        try {
            return Boolean.TRUE.equals(tx.execute(s -> {
                Booking b = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
                if (b == null || b.getHoldStatus() != HoldStatus.RELEASE_PENDING
                        || BookingService.INVENTORY_HOLDING_STATUSES.contains(b.getBookingStatus())) {
                    return false;
                }
                catalogClient.release(b.getHoldKey());
                b.setHoldStatus(HoldStatus.RELEASED);
                log.info("Rooms of booking {} released in catalog (hold {})", bookingId, b.getHoldKey());
                return true;
            }));
        } catch (RuntimeException e) {
            log.warn("Could not release the rooms of booking {} yet (will retry): {}", bookingId, e.getMessage());
            return false;
        }
    }
}
