package com.HotelManagement.AirBnb.review.service;

import com.HotelManagement.AirBnb.shared.logging.AuditLogger;
import com.HotelManagement.AirBnb.client.CatalogClient;
import com.HotelManagement.AirBnb.client.CatalogClient.RatingUpdate;
import com.HotelManagement.AirBnb.review.repository.HotelReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Keeps catalog-service's rating read model (used by guest search's minRating filter and result cards) in line with
 * the reviews here: the hotel's absolute (sum, count) is pushed after each new review, and every hotel is re-synced
 * on startup and periodically, so a push lost while catalog was down is repaired. Absolute values make every push
 * idempotent. The review summary endpoint itself always reads the reviews directly.
 */
@Component
public class RatingPublisher {

    private static final Logger log = LoggerFactory.getLogger(RatingPublisher.class);

    private final HotelReviewRepository reviewRepository;
    private final CatalogClient catalogClient;

    public RatingPublisher(HotelReviewRepository reviewRepository, CatalogClient catalogClient) {
        this.reviewRepository = reviewRepository;
        this.catalogClient = catalogClient;
    }

    /** Best effort; the periodic re-sync covers failures. */
    @Transactional(readOnly = true)
    public void publish(Long hotelId) {
        try {
            catalogClient.pushRatings(toUpdates(reviewRepository.ratingTotalsForHotel(hotelId)));
            log.debug("Pushed the rating of hotel {} to catalog", hotelId);
        } catch (RuntimeException e) {
            log.warn("Rating of hotel {} not pushed to catalog yet (re-sync will): {}", hotelId, e.getMessage());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelayString = "${app.reviews.rating-sync-ms:300000}", initialDelayString = "${app.reviews.rating-sync-ms:300000}")
    @Transactional(readOnly = true)
    public void syncAll() {
        AuditLogger.withJobId("rating-sync", this::syncAllNow);
    }

    private void syncAllNow() {
        try {
            List<RatingUpdate> updates = toUpdates(reviewRepository.ratingTotals());
            catalogClient.pushRatings(updates);
            log.debug("Re-synced ratings of {} hotel(s) to catalog", updates.size());
        } catch (RuntimeException e) {
            log.warn("Rating re-sync to catalog failed (will retry): {}", e.getMessage());
        }
    }

    private static List<RatingUpdate> toUpdates(List<Object[]> rows) {
        return rows.stream().map(r -> new RatingUpdate(((Number) r[0]).longValue(), ((Number) r[1]).longValue(),
                ((Number) r[2]).longValue())).toList();
    }
}
