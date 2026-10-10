package com.HotelManagement.AirBnb.repository;

import com.HotelManagement.AirBnb.entity.Booking;
import com.HotelManagement.AirBnb.repository.query.BookingQueries;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    /** Bookings still holding inventory whose stay has not finished yet. */
    @Query(value = BookingQueries.EXISTS_UPCOMING_FOR_HOTEL, nativeQuery = true)
    boolean existsUpcomingForHotel(@Param("hotelId") Long hotelId, @Param("statuses") Collection<String> statuses,
                                   @Param("today") LocalDate today);

    @Query(value = BookingQueries.EXISTS_UPCOMING_FOR_ROOM, nativeQuery = true)
    boolean existsUpcomingForRoom(@Param("roomId") Long roomId, @Param("statuses") Collection<String> statuses,
                                  @Param("today") LocalDate today);

    @Query(value = BookingQueries.EXISTS_FOR_HOTEL_BY_USERS, nativeQuery = true)
    boolean existsForHotelByUsers(@Param("hotelId") Long hotelId, @Param("userIds") Collection<Long> userIds);

    /** [bookingId, guestCount] for a page of bookings. */
    @Query(value = BookingQueries.COUNT_GUESTS, nativeQuery = true)
    List<Object[]> countGuests(@Param("ids") Collection<Long> ids);
}
