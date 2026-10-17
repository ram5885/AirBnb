package com.HotelManagement.AirBnb.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A guest's rating (1-5) and comment for a hotel, tied to the completed booking it reviews: at most one
 * review per booking (unique booking_id), so every review is a verified stay. Reviews are final (no edits
 * or deletes), and hotel ratings are aggregated from these rows on read rather than cached on the hotel.
 * Hotel, user and booking live in other services and are referenced by id; the reviewer's name, the room type and
 * the stay date are copied when the review is written (they are what the public review card shows).
 */
@Entity
@Table(name = "hotel_review")
@Getter
@Setter
public class HotelReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long hotelId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private Long bookingId;

    @Column(nullable = false)
    private Short rating;

    @Column(nullable = false, length = 1000)
    private String comment;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Snapshot for the public card: reviewer's name (only the first name is ever shown), stay and room type.
    private String reviewerName;

    private LocalDate stayedOn;

    private String roomType;
}
