package com.HotelManagement.AirBnb.review.controller;

import com.HotelManagement.AirBnb.auth.service.CurrentUserService;
import com.HotelManagement.AirBnb.review.dto.CreateReviewRequest;
import com.HotelManagement.AirBnb.review.dto.ReviewEligibilityResponse;
import com.HotelManagement.AirBnb.review.dto.ReviewResponse;
import com.HotelManagement.AirBnb.review.dto.ReviewSummaryResponse;
import com.HotelManagement.AirBnb.review.service.ReviewService;
import com.HotelManagement.AirBnb.shared.dto.PageResponse;
import com.HotelManagement.AirBnb.shared.exception.BadRequestException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * Hotel reviews: public reading (list + summary) for active hotels, and review submission / eligibility
 * for guests with completed stays (GUEST role, see SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/hotels/{hotelId}/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final CurrentUserService currentUser;

    public ReviewController(ReviewService reviewService, CurrentUserService currentUser) {
        this.reviewService = reviewService;
        this.currentUser = currentUser;
    }

    /**
     * <b>POST /api/v1/hotels/{hotelId}/reviews</b> — review a completed stay.
     * <p>
     * Body: {@code {"bookingId", "rating" (1–5), "comment" (10–1000 characters after trimming)}}. The
     * reviewer is the logged-in guest. The booking must be theirs, at this hotel, CONFIRMED with its
     * payment complete (paid online or collected at the hotel), with the check-out date reached, and not
     * reviewed before. Reviews can't be edited or deleted.
     * <p>
     * Access: GUEST.<br>
     * 201 Created: the review. 400: invalid fields, or the booking is for another hotel. 401: not logged
     * in. 403: not a guest account. 404: unknown/inactive hotel, or not the caller's booking.
     * 409: stay not completed (cancelled, unpaid, before check-out) or already reviewed.
     *
     * @param hotelId hotel id
     * @param request booking, rating and comment
     * @return the created review
     */
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ReviewResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId,
                                 @Valid @RequestBody CreateReviewRequest request) {
        return reviewService.create(currentUser.require(jwt), hotelId, request);
    }

    /**
     * <b>GET /api/v1/hotels/{hotelId}/reviews?sort=recent|highest|lowest&amp;page=&amp;size=</b> — reviews.
     * <p>
     * A page (default size 10, at most 50) of {@code id, rating, comment, createdAt, reviewerName} (first
     * name only), {@code stayedOn, roomType, verifiedStay}. sort: recent (default, newest first), highest
     * or lowest (by rating, newest first within a rating).
     * <p>
     * Access: public.<br>
     * 200 OK (may be empty). 400: invalid sort or paging. 404: unknown or inactive hotel.
     *
     * @param hotelId hotel id
     * @return one page of reviews
     */
    @GetMapping
    public PageResponse<ReviewResponse> list(@PathVariable Long hotelId,
                                             @RequestParam(defaultValue = "recent") String sort,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        ReviewService.Sort order;
        try {
            order = ReviewService.Sort.valueOf(sort.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("sort must be recent, highest or lowest");
        }
        return reviewService.list(hotelId, order, page, size);
    }

    /**
     * <b>GET /api/v1/hotels/{hotelId}/reviews/summary</b> — overall rating.
     * <p>
     * {@code {"hotelId", "averageRating" (one decimal, null without reviews), "totalReviews",
     * "ratingDistribution": {"5": n, "4": n, "3": n, "2": n, "1": n}}}, computed from the review rows.
     * <p>
     * Access: public. 200 OK. 404: unknown or inactive hotel.
     *
     * @param hotelId hotel id
     * @return the summary
     */
    @GetMapping("/summary")
    public ReviewSummaryResponse summary(@PathVariable Long hotelId) {
        return reviewService.summary(hotelId);
    }

    /**
     * <b>GET /api/v1/hotels/{hotelId}/reviews/eligibility</b> — can I review this hotel?
     * <p>
     * The caller's CONFIRMED bookings at the hotel, latest check-out first, each with a status: REVIEWABLE,
     * REVIEWED (with the caller's review), UPCOMING (before check-out) or NOT_ELIGIBLE (with a reason).
     * Informational only; submitting re-checks everything.
     * <p>
     * Access: GUEST. 200 OK. 401/403: not logged in / not a guest. 404: unknown or inactive hotel.
     *
     * @param hotelId hotel id
     * @return the caller's stays at the hotel
     */
    @GetMapping("/eligibility")
    public ReviewEligibilityResponse eligibility(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId) {
        return reviewService.eligibility(currentUser.require(jwt), hotelId);
    }
}
