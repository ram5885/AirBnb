package com.HotelManagement.AirBnb.location.controller;

import com.HotelManagement.AirBnb.location.dto.LocationDto;
import com.HotelManagement.AirBnb.location.service.LocationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    // GET /api/locations              -> every location, sorted by state then city
    // GET /api/locations?state=Kerala -> cities in one state (for a dependent dropdown)
    @GetMapping
    public List<LocationDto> getLocations(@RequestParam(required = false) String state) {
        return locationService.getLocations(state);
    }

    @GetMapping("/states")
    public List<String> getStates() {
        return locationService.getStates();
    }
}
