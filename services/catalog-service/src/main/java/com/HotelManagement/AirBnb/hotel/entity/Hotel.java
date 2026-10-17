package com.HotelManagement.AirBnb.hotel.entity;

import com.HotelManagement.AirBnb.location.entity.Location;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(indexes = {
        @Index(name = "idx_hotel_owner", columnList = "owner_id"),
        @Index(name = "idx_hotel_location", columnList = "location_id") // guest search by location
})
@Getter
@Setter
public class Hotel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long hotelId;

    @Column(nullable = false)
    private String name;

    // Denormalized from location.city; Inventory and hotel search filter by city.
    private String city;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    // The hotel manager (auth-service user id) who owns this hotel. Never taken from the request body.
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    // Photos are stored as binary rows in hotel_photo (see HotelPhoto).

    @Column(columnDefinition = "TEXT[]")
    private String[] amenities;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Embedded
    private HotelContactInfo contactInfo;

    @Column(nullable = false)
    private Boolean active;

    // Soft delete: set when the manager deletes the hotel, so booking history is kept.
    private LocalDateTime deletedAt;
}
