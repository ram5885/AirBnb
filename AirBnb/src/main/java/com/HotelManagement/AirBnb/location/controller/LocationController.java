package com.HotelManagement.AirBnb.location.controller;

import com.HotelManagement.AirBnb.location.dto.LocationDto;
import com.HotelManagement.AirBnb.location.service.LocationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Indian cities and states from the location table (public, read-only). Used by the hotel form's
 * state/city pickers and the guest search's city autocomplete. City names can repeat across states,
 * so clients should always keep the city together with its state.
 */
@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    /**
     * <b>GET /api/locations[?state=]</b> — list cities.
     * <p>
     * Without {@code state}: every location ({@code id, city, state}) sorted by state, then city. Used to
     * build the guest search's "City, State" autocomplete.<br>
     * With {@code state}: only that state's cities (case-insensitive), sorted by city. Used by the hotel
     * form's city picker after a state is chosen. An unknown state returns an empty list.
     * <p>
     * Access: public. 200 OK.
     *
     * @param state optional state name to filter by
     * @return matching locations
     */
    @GetMapping
    public List<LocationDto> getLocations(@RequestParam(required = false) String state) {
        return locationService.getLocations(state);
    }

    /**
     * <b>GET /api/locations/states</b> — list all states.
     * <p>
     * Distinct state/UT names that have at least one city, sorted alphabetically. Used by the hotel
     * form's state picker.
     * <p>
     * Access: public. 200 OK.
     *
     * @return state names
     */
    @GetMapping("/states")
    public List<String> getStates() {
        return locationService.getStates();
    }
}
