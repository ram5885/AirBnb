package com.HotelManagement.AirBnb.hotel.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;


/**
 * A room type of a hotel (e.g. "Deluxe King"). totalCount is how many physical
 * rooms of this type the hotel has; individual rooms are not modelled.
 */
@Entity
@Table(indexes = @Index(name = "idx_room_hotel", columnList = "hotel_id"))
@Getter
@Setter
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    // Photos are stored as binary rows in room_photo (see RoomPhoto).

    @Column(columnDefinition = "TEXT[]")
    private String[] amenities;

    @Column(nullable = false)
    private Integer totalCount;

    @Column(nullable = false)
    private Integer capacity;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Soft delete: set when the manager deletes the room type, so booking history is kept.
    private LocalDateTime deletedAt;

}
