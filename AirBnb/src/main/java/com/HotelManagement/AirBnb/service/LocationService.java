package com.HotelManagement.AirBnb.service;

import com.HotelManagement.AirBnb.dto.LocationDto;
import com.HotelManagement.AirBnb.repository.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class LocationService {

    private final LocationRepository locationRepository;

    public LocationService(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    /** All locations, or only those in {@code state} when it is given. */
    public List<LocationDto> getLocations(String state) {
        var locations = (state == null || state.isBlank())
                ? locationRepository.findAllOrderedByStateAndCity()
                : locationRepository.findByState(state.trim());
        return locations.stream().map(LocationDto::from).toList();
    }

    public List<String> getStates() {
        return locationRepository.findDistinctStates();
    }
}
