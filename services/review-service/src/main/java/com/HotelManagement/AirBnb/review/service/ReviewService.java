package com.HotelManagement.AirBnb.review.service;

import com.HotelManagement.AirBnb.shared.logging.AuditLogger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.HotelManagement.AirBnb.client.AuthClient;
import com.HotelManagement.AirBnb.client.BookingClient;
import com.HotelManagement.AirBnb.client.BookingClient.StayView;
import com.HotelManagement.AirBnb.client.CatalogClient;
import com.HotelManagement.AirBnb.review.dto.CreateReviewRequest;
import com.HotelManagement.AirBnb.review.dto.ReviewEligibilityResponse;
import com.HotelManagement.AirBnb.review.dto.ReviewEligibilityResponse.OwnReview;
import com.HotelManagement.AirBnb.review.dto.ReviewEligibilityResponse.Status;
import com.HotelManagement.AirBnb.review.dto.ReviewEligibilityResponse.StayStatus;
import com.HotelManagement.AirBnb.review.dto.ReviewResponse;
import com.HotelManagement.AirBnb.review.dto.ReviewSummaryResponse;
import com.HotelManagement.AirBnb.review.entity.HotelReview;
import com.HotelManagement.AirBnb.review.repository.HotelReviewRepository;
import com.HotelManagement.AirBnb.shared.dto.PageResponse;
import com.HotelManagement.AirBnb.shared.exception.BadRequestException;
import com.HotelManagement.AirBnb.shared.exception.ConflictException;
import com.HotelManagement.AirBnb.shared.exception.UnauthorizedException;
import com.HotelManagement.AirBnb.shared.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Hotel reviews. A stay can be reviewed once, by the guest who booked it, after it is complete: the booking
 * is CONFIRMED, its payment is CONFIRMED (paid online, or collected at the hotel by the manager) and the
 * check-out date has arrived. The app has no check-in/check-out tracking, so this is the strongest proof of
 * a stay available; cancelled, expired, unpaid, refunded and future bookings never qualify. All checks run
 * here on every submission against booking-service's current data, backed by a unique booking_id constraint
 * (which also settles concurrent duplicate submissions). Hotel visibility comes from catalog-service.
 */
@Service
@Transactional(readOnly = true)
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    public static final int MAX_PAGE_SIZE = 50;

    public enum Sort { RECENT, HIGHEST, LOWEST }

    private final HotelReviewRepository reviewRepository;
    private final CatalogClient catalogClient;
    private final BookingClient bookingClient;
    private final AuthClient authClient;
    private final RatingPublisher ratingPublisher;

    public ReviewService(HotelReviewRepository reviewRepository, CatalogClient catalogClient,
                         BookingClient bookingClient, AuthClient authClient, RatingPublisher ratingPublisher) {
        this.reviewRepository = reviewRepository;
        this.catalogClient = catalogClient;
        this.bookingClient = bookingClient;
        this.authClient = authClient;
        this.ratingPublisher = ratingPublisher;
    }

    @Transactional
    public ReviewResponse create(CurrentUser user, Long hotelId, CreateReviewRequest request) {
        requirePublicHotel(hotelId);
        // Only the caller's own bookings are found; someone else's booking id looks like a missing one.
        StayView booking = bookingClient.requireUsersBooking(request.bookingId(), user.getId());
        if (!booking.hotelId().equals(hotelId)) {
            log.info("Review refused: booking {} is for hotel {}, not {}", booking.bookingId(), booking.hotelId(), hotelId);
            throw new BadRequestException("This booking is for a different hotel");
        }
        String reason = ineligibleReason(booking, LocalDate.now());
        if (reason != null) {
            log.info("Review of booking {} refused: {}", booking.bookingId(), reason);
            AuditLogger.failure("REVIEW_CREATED", "hotelId", hotelId, "bookingId", booking.bookingId(), "reason", reason);
            throw new ConflictException(reason);
        }
        if (reviewRepository.existsForBooking(booking.bookingId())) {
            log.info("Review of booking {} refused: already reviewed", booking.bookingId());
            AuditLogger.failure("REVIEW_CREATED", "hotelId", hotelId, "bookingId", booking.bookingId(), "reason", "already reviewed");
            throw new ConflictException("You've already reviewed this stay");
        }
        String comment = cleanComment(request.comment());
        String reviewerName = authClient.findUser(user.getId()).map(AuthClient.UserView::name)
                .orElseThrow(() -> new UnauthorizedException("Your session has expired. Please log in again."));

        HotelReview review = new HotelReview();
        review.setHotelId(hotelId);
        review.setUserId(user.getId());
        review.setBookingId(booking.bookingId());
        review.setRating(request.rating().shortValue());
        review.setComment(comment);
        review.setReviewerName(reviewerName);
        review.setStayedOn(booking.checkInDate());
        review.setRoomType(booking.roomType());
        try {
            reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("You've already reviewed this stay"); // a concurrent duplicate lost the race
        }
        log.info("Review {} ({} stars) written for booking {} at hotel {}", review.getId(), review.getRating(),
                booking.bookingId(), hotelId);
        AuditLogger.success("REVIEW_CREATED", "reviewId", review.getId(), "hotelId", hotelId, "bookingId", booking.bookingId(),
                "rating", review.getRating());
        // Search's rating filter reads catalog's copy of the hotel's rating: update it once this is committed.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ratingPublisher.publish(hotelId);
            }
        });
        return new ReviewResponse(review.getId(), review.getRating(), comment, review.getCreatedAt(),
                firstName(reviewerName), booking.checkInDate(), booking.roomType(), true);
    }

    public PageResponse<ReviewResponse> list(Long hotelId, Sort sort, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("page must be 0 or more and size between 1 and " + MAX_PAGE_SIZE);
        }
        requirePublicHotel(hotelId);
        PageRequest pageable = PageRequest.of(page, size); // ordered in the query
        Page<Object[]> rows = switch (sort) {
            case RECENT -> reviewRepository.findRecent(hotelId, pageable);
            case HIGHEST -> reviewRepository.findHighest(hotelId, pageable);
            case LOWEST -> reviewRepository.findLowest(hotelId, pageable);
        };
        return PageResponse.of(rows, r -> new ReviewResponse(((Number) r[0]).longValue(), ((Number) r[1]).intValue(),
                (String) r[2], toDateTime(r[3]), firstName((String) r[4]), toDate(r[5]), (String) r[6], true));
    }

    /** One GROUP BY query; the average is computed exactly from the counts, then rounded to one decimal. */
    public ReviewSummaryResponse summary(Long hotelId) {
        requirePublicHotel(hotelId);
        Map<Integer, Long> counts = new HashMap<>();
        for (Object[] row : reviewRepository.countByRating(hotelId)) {
            counts.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        long total = 0;
        long sum = 0;
        for (int stars = 5; stars >= 1; stars--) {
            long n = counts.getOrDefault(stars, 0L);
            distribution.put(stars, n);
            total += n;
            sum += n * stars;
        }
        BigDecimal average = total == 0 ? null
                : BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
        return new ReviewSummaryResponse(hotelId, average, total, distribution);
    }

    public ReviewEligibilityResponse eligibility(CurrentUser user, Long hotelId) {
        requirePublicHotel(hotelId);
        List<StayView> bookings = bookingClient.confirmedStays(user.getId(), hotelId);
        Map<Long, OwnReview> reviews = bookings.isEmpty() ? Map.of()
                : reviewRepository.findByBookingIds(bookings.stream().map(StayView::bookingId).toList()).stream()
                .collect(Collectors.toMap(r -> ((Number) r[0]).longValue(), r -> new OwnReview(((Number) r[1]).longValue(),
                        ((Number) r[2]).intValue(), (String) r[3], toDateTime(r[4])), (a, b) -> a));
        LocalDate today = LocalDate.now();
        List<StayStatus> stays = bookings.stream().map(b -> {
            OwnReview review = reviews.get(b.bookingId());
            String reason = ineligibleReason(b, today);
            Status status = review != null ? Status.REVIEWED
                    : reason == null ? Status.REVIEWABLE
                    : b.checkOutDate().isAfter(today) ? Status.UPCOMING
                    : Status.NOT_ELIGIBLE;
            return new StayStatus(b.bookingId(), b.roomType(), b.checkInDate(), b.checkOutDate(),
                    status, status == Status.REVIEWABLE || status == Status.REVIEWED ? null : reason, review);
        }).toList();
        return new ReviewEligibilityResponse(hotelId, stays);
    }

    // ------------------------------------------------------------ helpers

    /** Why the booking can't be reviewed (yet), or null when it is a completed stay. */
    static String ineligibleReason(StayView b, LocalDate today) {
        if (!"CONFIRMED".equals(b.status())) {
            return "Only completed stays can be reviewed (this booking is " + b.status() + ")";
        }
        if (!"CONFIRMED".equals(b.paymentStatus())) {
            return "This stay can be reviewed once its payment is complete";
        }
        if (b.checkOutDate().isAfter(today)) {
            return "You can review this stay after check-out on " + b.checkOutDate();
        }
        return null;
    }

    /** Trimmed, control characters removed (newlines kept), whitespace runs within lines collapsed. */
    static String cleanComment(String raw) {
        String c = raw.replaceAll("\\r\\n?", "\n").replaceAll("[\\p{Cntrl}&&[^\\n]]", "")
                .replaceAll("[ \\t\\x0B\\f]+", " ").replaceAll("\\n{3,}", "\n\n").strip();
        if (c.length() < CreateReviewRequest.COMMENT_MIN) {
            throw new BadRequestException("Write at least " + CreateReviewRequest.COMMENT_MIN + " characters about your stay");
        }
        if (c.length() > CreateReviewRequest.COMMENT_MAX) {
            throw new BadRequestException("Keep your review under " + CreateReviewRequest.COMMENT_MAX + " characters");
        }
        return c;
    }

    /** Public display name: first name only. */
    static String firstName(String name) {
        if (name == null || name.isBlank()) {
            return "Guest";
        }
        return name.strip().split("\\s+")[0];
    }

    /** 404 "Hotel not found" unless guests can see the hotel (catalog-service decides). */
    private void requirePublicHotel(Long hotelId) {
        catalogClient.requirePublicHotel(hotelId);
    }

    private static LocalDateTime toDateTime(Object v) {
        return v instanceof Timestamp t ? t.toLocalDateTime() : (LocalDateTime) v;
    }

    private static LocalDate toDate(Object v) {
        return v instanceof Date d ? d.toLocalDate() : (LocalDate) v;
    }
}
