package com.HotelManagement.AirBnb.guest.repository;

import com.HotelManagement.AirBnb.guest.entity.Guest;
import com.HotelManagement.AirBnb.guest.repository.query.GuestQueries;
import com.HotelManagement.AirBnb.shared.repository.WriteRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GuestRepository extends WriteRepository<Guest, Long> {

    @Query(value = GuestQueries.FIND_BY_USER, nativeQuery = true)
    List<Guest> findByUser(@Param("userId") Long userId);

    @Query(value = GuestQueries.FIND_BY_IDS_FOR_USER, nativeQuery = true)
    List<Guest> findByIdsForUser(@Param("ids") Collection<Long> ids, @Param("userId") Long userId);

    @Query(value = GuestQueries.FIND_BY_ID_FOR_USER, nativeQuery = true)
    Optional<Guest> findByIdForUser(@Param("id") Long id, @Param("userId") Long userId);
}
