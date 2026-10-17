package com.HotelManagement.AirBnb.hotel.hold;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Rooms of one room type taken out of inventory for one booking (owned by booking-service), identified by the
 * hold key booking-service generates. Every inventory-holding booking (RESERVED, ACCEPTED, CONFIRMED) has a HELD
 * hold; cancelled/expired ones have a RELEASED hold. Replaces the monolith's single booking+inventory
 * transaction: taking, releasing and re-taking rooms happens here under the inventory row locks, idempotently
 * per hold key.
 */
@Entity
@Table(name = "inventory_hold")
@Getter
@Setter
public class InventoryHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID holdKey;

    // Set by booking-service once its booking row is committed ("attach"); null for a not-yet-attached hold.
    @Column(unique = true)
    private Long bookingId;

    @Column(nullable = false)
    private Long hotelId;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private LocalDate checkInDate;

    @Column(nullable = false)
    private LocalDate checkOutDate;

    @Column(nullable = false)
    private Integer roomsCount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HoldStatus status;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
