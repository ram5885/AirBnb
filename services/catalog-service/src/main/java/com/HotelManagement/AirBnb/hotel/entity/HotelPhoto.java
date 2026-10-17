package com.HotelManagement.AirBnb.hotel.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "hotel_photo", indexes = @Index(name = "idx_hotel_photo_hotel", columnList = "hotel_id"))
@Getter
@Setter
public class HotelPhoto extends PhotoBase {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;
}
