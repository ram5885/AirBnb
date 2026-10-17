package com.HotelManagement.AirBnb.hotel.rating;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Read model of a hotel's review rating, owned by review-service and pushed here (absolute sum and count) so
 * guest search can filter and sort by rating in SQL without reading another service's tables. Average shown =
 * ROUND(rating_sum / rating_count, 1), exactly as the monolith computed ROUND(AVG(rating), 1).
 */
@Entity
@Table(name = "hotel_rating")
@Getter
@Setter
public class HotelRating {

    @Id
    private Long hotelId;

    @Column(nullable = false)
    private Long ratingSum;

    @Column(nullable = false)
    private Long ratingCount;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
