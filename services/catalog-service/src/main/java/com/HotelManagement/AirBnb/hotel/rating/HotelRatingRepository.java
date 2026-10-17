package com.HotelManagement.AirBnb.hotel.rating;

import com.HotelManagement.AirBnb.shared.repository.WriteRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface HotelRatingRepository extends WriteRepository<HotelRating, Long> {

    /** Rows of [hotel_id, average rounded to one decimal, count] for hotels with reviews (others are absent). */
    @Query(value = """
            SELECT r.hotel_id, ROUND(CAST(r.rating_sum AS numeric) / r.rating_count, 1), r.rating_count
            FROM hotel_rating r WHERE r.hotel_id IN (:hotelIds) AND r.rating_count > 0""", nativeQuery = true)
    List<Object[]> ratingsByHotelIds(@Param("hotelIds") Collection<Long> hotelIds);

    /** Absolute values, so repeated pushes and re-syncs are idempotent. */
    @Modifying
    @Query(value = """
            INSERT INTO hotel_rating (hotel_id, rating_sum, rating_count, updated_at)
            VALUES (:hotelId, :ratingSum, :ratingCount, NOW())
            ON CONFLICT (hotel_id) DO UPDATE
            SET rating_sum = EXCLUDED.rating_sum, rating_count = EXCLUDED.rating_count, updated_at = NOW()""",
            nativeQuery = true)
    int upsert(@Param("hotelId") Long hotelId, @Param("ratingSum") long ratingSum, @Param("ratingCount") long ratingCount);
}
