package com.HotelManagement.AirBnb.booking.repository;

import com.HotelManagement.AirBnb.booking.entity.Booking;
import com.HotelManagement.AirBnb.booking.repository.query.BookingQueries;
import com.HotelManagement.AirBnb.shared.repository.WriteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Booking statuses are passed as their enum names (e.g. "RESERVED"). Pageables must be unsorted. */
public interface BookingRepository extends WriteRepository<Booking, Long> {

    @Query(value = BookingQueries.COUNT_ALL, nativeQuery = true)
    long countAll();

    @Query(value = BookingQueries.FIND_BY_ID_FOR_UPDATE, nativeQuery = true)
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    // ---- guest-side lookups: always scoped to the booking's owner (the authenticated user)

    @Query(value = BookingQueries.FIND_FOR_USER, nativeQuery = true)
    Optional<Booking> findForUser(@Param("id") Long id, @Param("userId") Long userId);

    @Query(value = BookingQueries.FIND_FOR_USER_FOR_UPDATE, nativeQuery = true)
    Optional<Booking> findForUserForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    /** The user's CONFIRMED bookings at the hotel, latest check-out first. */
    @Query(value = BookingQueries.FIND_CONFIRMED_FOR_USER_AT_HOTEL, nativeQuery = true)
    List<Booking> findConfirmedForUserAtHotel(@Param("userId") Long userId, @Param("hotelId") Long hotelId);

    @Query(value = BookingQueries.FIND_USER_PAGE, countQuery = BookingQueries.COUNT_USER_PAGE, nativeQuery = true)
    Page<Booking> findUserPage(@Param("userId") Long userId, @Param("statuses") Collection<String> statuses,
                               @Param("checkoutAfter") LocalDate checkoutAfter,
                               @Param("checkoutOnOrBefore") LocalDate checkoutOnOrBefore,
                               @Param("orderByCheckIn") boolean orderByCheckIn, Pageable pageable);

    // ---- hotel-manager lookups: scoped to hotels the manager owns

    @Query(value = BookingQueries.FIND_FOR_OWNER, nativeQuery = true)
    Optional<Booking> findForOwner(@Param("id") Long id, @Param("ownerId") Long ownerId);

    @Query(value = BookingQueries.FIND_FOR_OWNER_FOR_UPDATE, nativeQuery = true)
    Optional<Booking> findForOwnerForUpdate(@Param("id") Long id, @Param("ownerId") Long ownerId);

    @Query(value = BookingQueries.FIND_OWNER_PAGE, countQuery = BookingQueries.COUNT_OWNER_PAGE, nativeQuery = true)
    Page<Booking> findOwnerPage(@Param("ownerId") Long ownerId, @Param("hotelId") Long hotelId,
                                @Param("status") String status, @Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate, @Param("bookingId") Long bookingId,
                                Pageable pageable);

    /** [bookingStatus name, count] for the owner's hotels. */
    @Query(value = BookingQueries.COUNT_BY_STATUS_FOR_OWNER, nativeQuery = true)
    List<Object[]> countByStatusForOwner(@Param("ownerId") Long ownerId);

    // ---- shared

    /** Unpaid holds older than the cutoff (candidates for expiry). */
    @Query(value = BookingQueries.FIND_STALE_HOLD_IDS, nativeQuery = true)
    List<Long> findStaleHoldIds(@Param("cutoff") LocalDateTime cutoff);

    @Query(value = BookingQueries.FIND_BY_HOLD_KEY, nativeQuery = true)
    Optional<Booking> findByHoldKey(@Param("holdKey") UUID holdKey);

    /** Cancelled/expired bookings still waiting for catalog to confirm the rooms were given back. */
    @Query(value = BookingQueries.FIND_RELEASE_PENDING_IDS, nativeQuery = true)
    List<Long> findReleasePendingIds(@Param("before") LocalDateTime before);

    /** [bookingId, guestCount] for a page of bookings. */
    @Query(value = BookingQueries.COUNT_GUESTS, nativeQuery = true)
    List<Object[]> countGuests(@Param("ids") Collection<Long> ids);
}
