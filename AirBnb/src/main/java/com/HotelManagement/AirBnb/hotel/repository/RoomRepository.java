package com.HotelManagement.AirBnb.repository;

import com.HotelManagement.AirBnb.entity.Room;
import com.HotelManagement.AirBnb.repository.query.RoomQueries;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends WriteRepository<Room, Long> {

    @Query(value = RoomQueries.COUNT_ALL, nativeQuery = true)
    long countAll();

    @Query(value = RoomQueries.FIND_LIVE_BY_HOTEL, nativeQuery = true)
    List<Room> findLiveByHotel(@Param("hotelId") Long hotelId);

    @Query(value = RoomQueries.FIND_LIVE_IN_HOTEL, nativeQuery = true)
    Optional<Room> findLiveInHotel(@Param("id") Long id, @Param("hotelId") Long hotelId);

    @Query(value = RoomQueries.FIND_LIVE_IN_HOTEL_FOR_UPDATE, nativeQuery = true)
    Optional<Room> findForUpdate(@Param("id") Long id, @Param("hotelId") Long hotelId);

    /** Shared lock held while booking, so a concurrent room-type delete waits for the booking to finish. */
    @Query(value = RoomQueries.FIND_IN_HOTEL_FOR_SHARE, nativeQuery = true)
    Optional<Room> findByIdForShare(@Param("id") Long id, @Param("hotelId") Long hotelId);

    /** Includes soft-deleted room types (used by the demo seeder). */
    @Query(value = RoomQueries.EXISTS_TYPE_IN_HOTEL, nativeQuery = true)
    boolean existsTypeInHotel(@Param("hotelId") Long hotelId, @Param("type") String type);

    @Query(value = RoomQueries.EXISTS_LIVE_TYPE_IN_HOTEL, nativeQuery = true)
    boolean existsLiveTypeInHotel(@Param("hotelId") Long hotelId, @Param("type") String type);

    @Query(value = RoomQueries.EXISTS_OTHER_LIVE_TYPE_IN_HOTEL, nativeQuery = true)
    boolean existsOtherLiveTypeInHotel(@Param("hotelId") Long hotelId, @Param("type") String type,
                                       @Param("excludeId") Long excludeId);

    /** [hotelId, count] of live room types per hotel, for list pages without N+1 queries. */
    @Query(value = RoomQueries.COUNT_LIVE_BY_HOTEL_IDS, nativeQuery = true)
    List<Object[]> countLiveByHotelIds(@Param("hotelIds") Collection<Long> hotelIds);
}
