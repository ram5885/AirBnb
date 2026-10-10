package com.HotelManagement.AirBnb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A city within an Indian state/UT. City names are not globally unique
 * (the same name can exist in different states), so uniqueness is enforced
 * on the (city, state) pair. The unique index also serves city lookups.
 */
@Entity
@Table(
        name = "location",
        uniqueConstraints = @UniqueConstraint(name = "uk_location_city_state", columnNames = {"city", "state"}),
        indexes = @Index(name = "idx_location_state", columnList = "state")
)
@Getter
@Setter
@NoArgsConstructor
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    private String state;

    public Location(String city, String state) {
        this.city = city;
        this.state = state;
    }
}
