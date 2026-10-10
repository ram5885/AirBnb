package com.HotelManagement.AirBnb.repository;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * Base for all repositories: exposes only JPA's write operations. Every read (and bulk update/delete)
 * is an explicit native SQL query declared in the repository, with the SQL kept in the
 * {@code repository.query} package. Writes stay on JPA so generated ids, @CreationTimestamp /
 * @UpdateTimestamp and entity relationships keep working.
 */
@NoRepositoryBean
public interface WriteRepository<T, ID> extends Repository<T, ID> {

    <S extends T> S save(S entity);

    <S extends T> List<S> saveAll(Iterable<S> entities);

    <S extends T> S saveAndFlush(S entity);

    void flush();
}
