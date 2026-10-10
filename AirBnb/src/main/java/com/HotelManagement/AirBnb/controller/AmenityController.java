package com.HotelManagement.AirBnb.controller;

import com.HotelManagement.AirBnb.dto.AmenityCategoryResponse;
import com.HotelManagement.AirBnb.service.AmenityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/amenities")
public class AmenityController {

    private final AmenityService amenityService;

    public AmenityController(AmenityService amenityService) {
        this.amenityService = amenityService;
    }

    @GetMapping
    public List<AmenityCategoryResponse> getAmenities() {
        return amenityService.catalog();
    }
}
