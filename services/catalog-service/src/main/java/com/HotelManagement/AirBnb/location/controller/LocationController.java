package com.HotelManagement.AirBnb.location.controller;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.HotelManagement.AirBnb.location.dto.DestinationResponse;
import com.HotelManagement.AirBnb.location.dto.LocationDto;
import com.HotelManagement.AirBnb.location.service.DestinationService;
import com.HotelManagement.AirBnb.location.service.LocationService;
import com.HotelManagement.AirBnb.shared.web.PhotoResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    private static final Logger log = LoggerFactory.getLogger(LocationController.class);

    private final LocationService locationService;
    private final DestinationService destinationService;

    public LocationController(LocationService locationService, DestinationService destinationService) {
        this.locationService = locationService;
        this.destinationService = destinationService;
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
        log.debug("GET /api/locations state={}", state);
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
        log.debug("GET /api/locations/states");
        return locationService.getStates();
    }

    /**
     * <b>GET /api/locations/destinations</b> — "Explore stays by city" on the guest home page.
     * <p>
     * Every city with at least one listed hotel (active, not deleted, with at least one room type),
     * most hotels first: {@code locationId, city, state, hotelCount}, {@code photoUrl} (the city's photo,
     * else a photo of one of its hotels, else null) and {@code photoCredit} (for city photos). Dates are
     * not considered: a city stays listed even when it has no free room on particular dates.
     * <p>
     * Access: public. 200 OK (may be empty).
     *
     * @return the destinations
     */
    @GetMapping("/destinations")
    public List<DestinationResponse> getDestinations() {
        log.debug("GET /api/locations/destinations");
        return destinationService.getDestinations();
    }

    /**
     * <b>GET /api/locations/{locationId}/photo</b> — a city's iconic photo.
     * <p>
     * Image bytes from the city_photo table (plain {@code <img src>} works, no token needed).
     * <p>
     * Access: public. 200 OK (image). 404: the city has no photo.
     *
     * @param locationId location id
     * @return the image
     */
    @GetMapping("/{locationId}/photo")
    public ResponseEntity<byte[]> getPhoto(@PathVariable Long locationId) {
        log.debug("GET /api/locations/{locationId}/photo locationId={}", locationId);
        return PhotoResponses.of(destinationService.getPhoto(locationId));
    }
}
