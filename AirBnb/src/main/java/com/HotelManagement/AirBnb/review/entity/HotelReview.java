package com.HotelManagement.AirBnb.review.entity;

import com.HotelManagement.AirBnb.auth.entity.User;
import com.HotelManagement.AirBnb.booking.entity.Booking;
import com.HotelManagement.AirBnb.hotel.entity.Hotel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A guest's rating (1-5) and comment for a hotel, tied to the completed booking it reviews: at most one
 * review per booking (unique booking_id), so every review is a verified stay. Reviews are final (no edits
 * or deletes), and hotel ratings are aggregated from these rows on read rather than cached on the hotel.
 */
@Entity
@Table(
        name = "hotel_review",
        uniqueConstraints = @UniqueConstraint(name = "uk_hotel_review_booking", columnNames = "booking_id"),
        indexes = {
                // review list by newest, and summary / sort by rating, for one hotel
                @Index(name = "idx_hotel_review_hotel_created", columnList = "hotel_id, created_at"),
                @Index(name = "idx_hotel_review_hotel_rating", columnList = "hotel_id, rating")
        })
@Check(name = "chk_hotel_review_rating", constraints = "rating BETWEEN 1 AND 5")
@Getter
@Setter
public class HotelReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(nullable = false)
    private Short rating;

    @Column(nullable = false, length = 1000)
    private String comment;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
