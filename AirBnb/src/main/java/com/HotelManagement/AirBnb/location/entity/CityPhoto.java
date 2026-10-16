package com.HotelManagement.AirBnb.location.entity;

import com.HotelManagement.AirBnb.hotel.entity.PhotoBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * The iconic photo shown for a city on the guest home page's "Destinations for you" row: at most one
 * per location. Photos come from Wikimedia Commons (see CityPhotoLoader), so each keeps its source and
 * licence for attribution.
 */
@Entity
@Table(name = "city_photo", uniqueConstraints = @UniqueConstraint(name = "uk_city_photo_location", columnNames = "location_id"))
@Getter
@Setter
public class CityPhoto extends PhotoBase {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    /** Wikipedia article the photo was taken from. */
    @Column(length = 500)
    private String sourcePage;

    /** Wikimedia Commons file page (author, licence and full-size original). */
    @Column(length = 500)
    private String imagePage;

    @Column(length = 200)
    private String artist;

    @Column(length = 100)
    private String license;
}
