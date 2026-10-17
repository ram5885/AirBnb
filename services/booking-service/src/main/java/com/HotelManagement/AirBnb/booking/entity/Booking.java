package com.HotelManagement.AirBnb.booking.entity;

import com.HotelManagement.AirBnb.guest.entity.Guest;
import com.HotelManagement.AirBnb.payment.entity.Payment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A guest's booking. Hotels and room types (catalog-service) and users (auth-service) are referenced by id;
 * their names are kept as a snapshot taken at booking time so views still render when those services are
 * unavailable (live values are preferred, see BookingViews). The rooms themselves are held in catalog's
 * inventory under {@link #holdKey}.
 */
@Entity
@Table(name = "booking")
@Getter
@Setter
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookingId;

    @Column(nullable = false)
    private Long hotelId;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private Long userId;

    // The hotel's manager (catalog hotel.owner_id; never changes): scopes the manager's booking views.
    @Column(nullable = false)
    private Long hotelOwnerId;

    @Column(nullable = false)
    private Integer roomsCount;

    // Total for all nights and rooms, computed by catalog from inventory prices when the rooms were held.
    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate checkInDate;

    @Column(nullable = false)
    private LocalDate checkOutDate;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus bookingStatus;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "booking_guest",
            joinColumns = @JoinColumn(name = "booking_id"),
            inverseJoinColumns = @JoinColumn(name = "guest_id")
    )
    private Set<Guest> guests;

    // ---- inventory hold in catalog-service

    @Column(nullable = false, unique = true, updatable = false)
    private UUID holdKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HoldStatus holdStatus;

    // ---- snapshot of catalog / auth data at booking time

    private String hotelName;

    private String hotelCity;

    private String hotelState;

    private String roomType;

    private Integer roomCapacity;

    private String userName;

    private String userEmail;
}
