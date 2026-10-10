package com.HotelManagement.AirBnb.repository;

import com.HotelManagement.AirBnb.entity.User;
import com.HotelManagement.AirBnb.repository.query.UserQueries;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends WriteRepository<User, Long> {

    @Query(value = UserQueries.FIND_BY_ID, nativeQuery = true)
    Optional<User> findUserById(@Param("id") Long id);

    @Query(value = UserQueries.FIND_BY_EMAIL, nativeQuery = true)
    Optional<User> findByEmail(@Param("email") String email);

    @Query(value = UserQueries.EXISTS_BY_EMAIL, nativeQuery = true)
    boolean existsByEmail(@Param("email") String email);

    @Query(value = UserQueries.COUNT_ALL, nativeQuery = true)
    long countAll();
}
