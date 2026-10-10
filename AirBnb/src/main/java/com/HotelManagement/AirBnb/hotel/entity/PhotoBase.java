package com.HotelManagement.AirBnb.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Image stored in the database (PostgreSQL bytea) with its metadata.
 * List/detail endpoints only select the metadata columns; the bytes are
 * streamed by dedicated photo endpoints.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class PhotoBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false, length = 50)
    private String contentType;

    @Column(nullable = false)
    private Long sizeBytes;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
