package com.HotelManagement.AirBnb.hotel.repository;

import com.HotelManagement.AirBnb.hotel.entity.Hotel;
import com.HotelManagement.AirBnb.hotel.repository.query.HotelQueries;
import com.HotelManagement.AirBnb.shared.repository.WriteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HotelRepository extends WriteRepository<Hotel, Long> {

    @Query(value = HotelQueries.COUNT_ALL, nativeQuery = true)
    long countAll();

    @Query(value = HotelQueries.FIND_BY_ID, nativeQuery = true)
    Optional<Hotel> findHotelById(@Param("id") Long id);

    /** Owned, not-deleted hotel. */
    @Query(value = HotelQueries.FIND_OWNED, nativeQuery = true)
    Optional<Hotel> findOwned(@Param("hotelId") Long hotelId, @Param("ownerId") Long ownerId);

    /** Same, with a row lock (SELECT ... FOR UPDATE). */
    @Query(value = HotelQueries.FIND_OWNED_FOR_UPDATE, nativeQuery = true)
    Optional<Hotel> findOwnedForUpdate(@Param("hotelId") Long hotelId, @Param("ownerId") Long ownerId);

    /** Owner's live hotels, newest first; active = null means all. Pass an unsorted Pageable. */
    @Query(value = HotelQueries.FIND_OWNER_PAGE, countQuery = HotelQueries.COUNT_OWNER_PAGE, nativeQuery = true)
    Page<Hotel> findOwnerPage(@Param("ownerId") Long ownerId, @Param("active") Boolean active, Pageable pageable);

    @Query(value = HotelQueries.COUNT_OWNED_LIVE, nativeQuery = true)
    long countOwnedLive(@Param("ownerId") Long ownerId);

    @Query(value = HotelQueries.COUNT_OWNED_ACTIVE, nativeQuery = true)
    long countOwnedActive(@Param("ownerId") Long ownerId);

    /** Includes soft-deleted hotels (used by the demo seeder so a deleted demo hotel isn't recreated). */
    @Query(value = HotelQueries.FIND_FIRST_BY_OWNER_AND_NAME, nativeQuery = true)
    Optional<Hotel> findFirstByOwnerAndName(@Param("ownerId") Long ownerId, @Param("name") String name);

    /** A hotel guests may see: active and not deleted. */
    @Query(value = HotelQueries.FIND_PUBLIC, nativeQuery = true)
    Optional<Hotel> findPublic(@Param("id") Long id);

    /** Guest search (see HotelQueries.SEARCH_WHERE). Pass an unsorted Pageable; results are ordered by name. */
    @Query(value = HotelQueries.SEARCH, countQuery = HotelQueries.SEARCH_COUNT, nativeQuery = true)
    Page<Hotel> searchAvailable(@Param("locationId") Long locationId, @Param("checkIn") LocalDate checkIn,
                                @Param("checkOut") LocalDate checkOut, @Param("rooms") int rooms,
                                @Param("nights") long nights, @Param("amenities") String amenities,
                                @Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice,
                                @Param("minRating") BigDecimal minRating, Pageable pageable);

    /** Rows of [hotel_id, location_id, available_in_city]; see HotelQueries.SECTION_HOTEL_IDS. */
    @Query(value = HotelQueries.SECTION_HOTEL_IDS, nativeQuery = true)
    List<Object[]> findSectionHotelIds(@Param("checkIn") LocalDate checkIn, @Param("checkOut") LocalDate checkOut,
                                       @Param("rooms") int rooms, @Param("nights") long nights,
                                       @Param("amenities") String amenities, @Param("minPrice") BigDecimal minPrice,
                                       @Param("maxPrice") BigDecimal maxPrice, @Param("perCity") int perCity);

    /** Hotels (including inactive and soft-deleted ones) with these ids, in no particular order. */
    @Query(value = HotelQueries.FIND_BY_IDS, nativeQuery = true)
    List<Hotel> findHotelsByIds(@Param("ids") Collection<Long> ids);

    /** Active, non-deleted hotels with these ids, in no particular order. */
    @Query(value = HotelQueries.FIND_PUBLIC_BY_IDS, nativeQuery = true)
    List<Hotel> findPublicByIds(@Param("ids") Collection<Long> ids);

    /**
     * Shared row lock (SELECT ... FOR SHARE) taken while creating a booking, so a concurrent
     * deactivation/deletion (which updates the row) waits and cannot race the active check.
     */
    @Query(value = HotelQueries.FIND_BY_ID_FOR_SHARE, nativeQuery = true)
    Optional<Hotel> findByIdForShare(@Param("id") Long id);
}
