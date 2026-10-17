package com.HotelManagement.AirBnb.location.service;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.HotelManagement.AirBnb.location.dto.LocationDto;
import com.HotelManagement.AirBnb.location.repository.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    private final LocationRepository locationRepository;

    public LocationService(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    /** All locations, or only those in {@code state} when it is given. */
    public List<LocationDto> getLocations(String state) {
        var locations = (state == null || state.isBlank())
                ? locationRepository.findAllOrderedByStateAndCity()
                : locationRepository.findByState(state.trim());
        log.debug("Listing locations (state={}): {}", state, locations.size());
        return locations.stream().map(LocationDto::from).toList();
    }

    public List<String> getStates() {
        return locationRepository.findDistinctStates();
    }
}
