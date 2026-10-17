package com.HotelManagement.AirBnb.booking.service;

import com.HotelManagement.AirBnb.shared.logging.AuditLogger;
import com.HotelManagement.AirBnb.booking.entity.Booking;
import com.HotelManagement.AirBnb.booking.repository.BookingRepository;
import com.HotelManagement.AirBnb.client.CatalogClient;
import com.HotelManagement.AirBnb.client.CatalogDtos.HoldResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Safety nets for the booking <-> inventory hold protocol (see docs/MICROSERVICES_ARCHITECTURE.md §5):
 * <ul>
 *   <li>retries releases that couldn't reach catalog-service when a booking was cancelled or expired;</li>
 *   <li>reconciles holds catalog has that no booking claimed (booking-service stopped between taking the rooms
 *       and saving the booking): attaches them when the booking exists, otherwise releases them.</li>
 * </ul>
 */
@Component
public class HoldMaintenanceJobs {

    private static final Logger log = LoggerFactory.getLogger(HoldMaintenanceJobs.class);

    /** Holds younger than this may belong to a booking that is being saved right now. */
    static final int UNATTACHED_GRACE_MINUTES = 10;

    private final BookingRepository bookingRepository;
    private final HoldReleaser holdReleaser;
    private final CatalogClient catalogClient;

    public HoldMaintenanceJobs(BookingRepository bookingRepository, HoldReleaser holdReleaser, CatalogClient catalogClient) {
        this.bookingRepository = bookingRepository;
        this.holdReleaser = holdReleaser;
        this.catalogClient = catalogClient;
    }

    @Scheduled(fixedDelayString = "${app.booking.release-retry-ms:60000}", initialDelayString = "${app.booking.release-retry-ms:60000}")
    public void retryPendingReleases() {
        AuditLogger.withJobId("release-retry", this::retryPendingReleasesNow);
    }

    private void retryPendingReleasesNow() {
        int released = 0;
        for (Long id : bookingRepository.findReleasePendingIds(LocalDateTime.now().minusSeconds(30))) {
            if (holdReleaser.releaseNow(id)) {
                released++;
            }
        }
        if (released > 0) {
            log.info("Released the rooms of {} cancelled/expired booking(s) on retry", released);
        }
    }

    @Scheduled(fixedDelayString = "${app.booking.reconcile-ms:300000}", initialDelayString = "${app.booking.reconcile-ms:300000}")
    public void reconcileUnattachedHolds() {
        AuditLogger.withJobId("hold-reconcile", this::reconcileUnattachedHoldsNow);
    }

    private void reconcileUnattachedHoldsNow() {
        List<HoldResponse> holds;
        try {
            holds = catalogClient.unattached(UNATTACHED_GRACE_MINUTES);
        } catch (RuntimeException e) {
            log.warn("Hold reconciliation skipped: {}", e.getMessage());
            return;
        }
        for (HoldResponse hold : holds) {
            try {
                Booking booking = bookingRepository.findByHoldKey(hold.holdKey()).orElse(null);
                if (booking != null) {
                    catalogClient.attach(hold.holdKey(), booking.getBookingId());
                    log.info("Attached hold {} to booking {} (missed at booking time)", hold.holdKey(), booking.getBookingId());
                } else {
                    catalogClient.release(hold.holdKey());
                    log.warn("Released orphan hold {} ({} room(s) of room type {} from {})", hold.holdKey(),
                            hold.roomsCount(), hold.roomId(), hold.checkInDate());
                    AuditLogger.success("ORPHAN_HOLD_RELEASED", "holdKey", hold.holdKey(), "roomId", hold.roomId(),
                            "rooms", hold.roomsCount());
                }
            } catch (RuntimeException e) {
                log.warn("Could not reconcile hold {}: {}", hold.holdKey(), e.getMessage());
            }
        }
    }
}
