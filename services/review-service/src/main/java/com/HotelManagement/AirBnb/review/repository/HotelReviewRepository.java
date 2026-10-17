package com.HotelManagement.AirBnb.review.repository;

import com.HotelManagement.AirBnb.review.entity.HotelReview;
import com.HotelManagement.AirBnb.review.repository.query.ReviewQueries;
import com.HotelManagement.AirBnb.shared.repository.WriteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Page rows are [id, rating, comment, created_at, reviewer_name, stayed_on, room_type]; pass unsorted Pageables. */
public interface HotelReviewRepository extends WriteRepository<HotelReview, Long> {

    @Query(value = ReviewQueries.PAGE_RECENT, countQuery = ReviewQueries.COUNT_PAGE, nativeQuery = true)
    Page<Object[]> findRecent(@Param("hotelId") Long hotelId, Pageable pageable);

    @Query(value = ReviewQueries.PAGE_HIGHEST, countQuery = ReviewQueries.COUNT_PAGE, nativeQuery = true)
    Page<Object[]> findHighest(@Param("hotelId") Long hotelId, Pageable pageable);

    @Query(value = ReviewQueries.PAGE_LOWEST, countQuery = ReviewQueries.COUNT_PAGE, nativeQuery = true)
    Page<Object[]> findLowest(@Param("hotelId") Long hotelId, Pageable pageable);

    /** Rows of [rating, count]. */
    @Query(value = ReviewQueries.RATING_COUNTS, nativeQuery = true)
    List<Object[]> countByRating(@Param("hotelId") Long hotelId);

    /** Rows of [booking_id, id, rating, comment, created_at]. */
    @Query(value = ReviewQueries.BY_BOOKING_IDS, nativeQuery = true)
    List<Object[]> findByBookingIds(@Param("bookingIds") Collection<Long> bookingIds);

    /** Rows of [hotel_id, average (one decimal), count]; hotels without reviews are absent. */
    @Query(value = ReviewQueries.RATINGS_BY_HOTEL_IDS, nativeQuery = true)
    List<Object[]> ratingsByHotelIds(@Param("hotelIds") Collection<Long> hotelIds);

    /** Rows of [hotel_id, sum of ratings, count]. */
    @Query(value = ReviewQueries.RATING_TOTALS, nativeQuery = true)
    List<Object[]> ratingTotals();

    @Query(value = ReviewQueries.RATING_TOTALS_FOR_HOTEL, nativeQuery = true)
    List<Object[]> ratingTotalsForHotel(@Param("hotelId") Long hotelId);

    @Query(value = ReviewQueries.EXISTS_FOR_BOOKING, nativeQuery = true)
    boolean existsForBooking(@Param("bookingId") Long bookingId);
}
