package com.HotelManagement.AirBnb.location.repository;

import com.HotelManagement.AirBnb.location.entity.CityPhoto;
import com.HotelManagement.AirBnb.location.repository.query.CityPhotoQueries;
import com.HotelManagement.AirBnb.shared.repository.WriteRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CityPhotoRepository extends WriteRepository<CityPhoto, Long> {

    @Query(value = CityPhotoQueries.FIND_BY_LOCATION, nativeQuery = true)
    Optional<CityPhoto> findByLocation(@Param("locationId") Long locationId);

    @Query(value = CityPhotoQueries.FIND_PHOTOGRAPHED_LOCATION_IDS, nativeQuery = true)
    List<Long> findPhotographedLocationIds();

}
