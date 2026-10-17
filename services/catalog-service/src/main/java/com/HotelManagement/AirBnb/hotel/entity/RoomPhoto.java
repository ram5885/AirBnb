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
@Table(name = "room_photo", indexes = @Index(name = "idx_room_photo_room", columnList = "room_id"))
@Getter
@Setter
public class RoomPhoto extends PhotoBase {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;
}
