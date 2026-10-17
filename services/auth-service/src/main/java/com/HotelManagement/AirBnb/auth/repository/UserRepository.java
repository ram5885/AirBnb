package com.HotelManagement.AirBnb.auth.repository;

import com.HotelManagement.AirBnb.auth.entity.User;
import com.HotelManagement.AirBnb.auth.repository.query.UserQueries;
import com.HotelManagement.AirBnb.shared.repository.WriteRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends WriteRepository<User, Long> {

    @Query(value = UserQueries.FIND_BY_ID, nativeQuery = true)
    Optional<User> findUserById(@Param("id") Long id);

    @Query(value = UserQueries.FIND_BY_IDS, nativeQuery = true)
    List<User> findUsersByIds(@Param("ids") Collection<Long> ids);

    @Query(value = UserQueries.FIND_BY_EMAIL, nativeQuery = true)
    Optional<User> findByEmail(@Param("email") String email);

    @Query(value = UserQueries.EXISTS_BY_EMAIL, nativeQuery = true)
    boolean existsByEmail(@Param("email") String email);

    @Query(value = UserQueries.COUNT_ALL, nativeQuery = true)
    long countAll();
}
