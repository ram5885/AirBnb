package com.HotelManagement.AirBnb.location.repository;

import com.HotelManagement.AirBnb.location.entity.Location;
import com.HotelManagement.AirBnb.location.repository.query.LocationQueries;
import com.HotelManagement.AirBnb.shared.repository.WriteRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LocationRepository extends WriteRepository<Location, Long> {

    @Query(value = LocationQueries.FIND_ALL, nativeQuery = true)
    List<Location> findAllLocations();

    @Query(value = LocationQueries.FIND_ALL_ORDERED, nativeQuery = true)
    List<Location> findAllOrderedByStateAndCity();

    @Query(value = LocationQueries.FIND_BY_STATE, nativeQuery = true)
    List<Location> findByState(@Param("state") String state);

    @Query(value = LocationQueries.FIND_BY_CITY_AND_STATE, nativeQuery = true)
    Optional<Location> findByCityAndState(@Param("city") String city, @Param("state") String state);

    /** All states that have a city with this name (city names aren't unique across states). */
    @Query(value = LocationQueries.FIND_BY_CITY, nativeQuery = true)
    List<Location> findByCity(@Param("city") String city);

    @Query(value = LocationQueries.FIND_DISTINCT_STATES, nativeQuery = true)
    List<String> findDistinctStates();

    /** Rows of [location_id, city, state, hotel_count, city_photo_id, artist, license, hotel_photo_hotel_id,
     *  hotel_photo_id]; see LocationQueries.DESTINATIONS. */
    @Query(value = LocationQueries.DESTINATIONS, nativeQuery = true)
    List<Object[]> findDestinationRows();

    @Query(value = LocationQueries.COUNT_ALL, nativeQuery = true)
    long countAll();
}
