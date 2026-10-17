package com.HotelManagement.AirBnb.hotel.controller;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.HotelManagement.AirBnb.hotel.dto.AmenityCategoryResponse;
import com.HotelManagement.AirBnb.hotel.service.AmenityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The catalog of amenities a hotel or room type can offer (public, read-only). Hotel and room-type
 * requests must use the {@code code} values returned here.
 */
@RestController
@RequestMapping("/api/v1/amenities")
public class AmenityController {

    private static final Logger log = LoggerFactory.getLogger(AmenityController.class);

    private final AmenityService amenityService;

    public AmenityController(AmenityService amenityService) {
        this.amenityService = amenityService;
    }

    /**
     * <b>GET /api/v1/amenities</b> — the amenity catalog, grouped by category.
     * <p>
     * Returns categories ({@code code, label}) each with their amenities ({@code code, label}), e.g.
     * "Internet and office" &rarr; Wi-Fi, Dedicated workspace. The frontend uses it to render the
     * amenity checkboxes and to show labels/icons for the codes stored on hotels and room types.
     * <p>
     * Access: public. 200 OK.
     *
     * @return amenity categories with their amenities
     */
    @GetMapping
    public List<AmenityCategoryResponse> getAmenities() {
        log.debug("GET /api/v1/amenities");
        return amenityService.catalog();
    }
}
