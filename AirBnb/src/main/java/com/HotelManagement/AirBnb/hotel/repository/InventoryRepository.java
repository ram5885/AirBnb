package com.HotelManagement.AirBnb.repository;

import com.HotelManagement.AirBnb.entity.Inventory;
import com.HotelManagement.AirBnb.repository.query.InventoryQueries;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface InventoryRepository extends WriteRepository<Inventory, Long> {

    /** Inventory rows for [start, end) of one room type. */
    @Query(value = InventoryQueries.FIND_RANGE, nativeQuery = true)
    List<Inventory> findRange(@Param("roomId") Long roomId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * Same as findRange but takes row locks (SELECT ... FOR UPDATE), always in date order so
     * concurrent bookings/cancellations lock rows in the same order and cannot deadlock.
     */
    @Query(value = InventoryQueries.FIND_RANGE_FOR_UPDATE, nativeQuery = true)
    List<Inventory> findRangeForUpdate(@Param("roomId") Long roomId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * Per live room type of the given hotels, over the open nights in [start, end):
     * [hotelId, roomId, openNights, sum(price), min(totalCount - bookedCount)].
     */
    @Query(value = InventoryQueries.AVAILABILITY_BY_ROOM, nativeQuery = true)
    List<Object[]> availabilityByRoom(@Param("hotelIds") Collection<Long> hotelIds,
                                      @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Modifying(flushAutomatically = true)
    @Query(value = InventoryQueries.CLOSE_ROOM_FROM, nativeQuery = true)
    int closeRoomFrom(@Param("roomId") Long roomId, @Param("from") LocalDate from);

    /** Creates one inventory row per day in [start, end) for a new room type in a single statement. */
    @Modifying(flushAutomatically = true)
    @Query(value = InventoryQueries.GENERATE, nativeQuery = true)
    int generate(@Param("hotelId") Long hotelId, @Param("roomId") Long roomId, @Param("totalCount") int totalCount,
                 @Param("price") BigDecimal price, @Param("city") String city,
                 @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Modifying(flushAutomatically = true)
    @Query(value = InventoryQueries.CLOSE_HOTEL_FROM, nativeQuery = true)
    int closeHotelFrom(@Param("hotelId") Long hotelId, @Param("from") LocalDate from);

    @Modifying(flushAutomatically = true)
    @Query(value = InventoryQueries.UPDATE_CITY_FOR_HOTEL, nativeQuery = true)
    int updateCityForHotel(@Param("hotelId") Long hotelId, @Param("city") String city);
}
