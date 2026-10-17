package com.HotelManagement.AirBnb.hotel.hold;

import com.HotelManagement.AirBnb.shared.repository.WriteRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryHoldRepository extends WriteRepository<InventoryHold, Long> {

    @Query(value = HoldQueries.FIND_BY_KEY, nativeQuery = true)
    Optional<InventoryHold> findByHoldKey(@Param("holdKey") UUID holdKey);

    /** Row lock: serializes release/reacquire/attach of the same hold. */
    @Query(value = HoldQueries.FIND_BY_KEY_FOR_UPDATE, nativeQuery = true)
    Optional<InventoryHold> findByHoldKeyForUpdate(@Param("holdKey") UUID holdKey);

    @Query(value = HoldQueries.FIND_UNATTACHED_BEFORE, nativeQuery = true)
    List<InventoryHold> findUnattachedBefore(@Param("cutoff") LocalDateTime cutoff);

    /** Holds still taking rooms for stays that haven't ended (bookings RESERVED/ACCEPTED/CONFIRMED). */
    @Query(value = HoldQueries.EXISTS_UPCOMING_FOR_HOTEL, nativeQuery = true)
    boolean existsUpcomingForHotel(@Param("hotelId") Long hotelId, @Param("today") LocalDate today);

    @Query(value = HoldQueries.EXISTS_UPCOMING_FOR_ROOM, nativeQuery = true)
    boolean existsUpcomingForRoom(@Param("roomId") Long roomId, @Param("today") LocalDate today);
}
