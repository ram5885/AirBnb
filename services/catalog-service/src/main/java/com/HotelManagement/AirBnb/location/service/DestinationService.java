package com.HotelManagement.AirBnb.location.service;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.HotelManagement.AirBnb.location.dto.DestinationResponse;
import com.HotelManagement.AirBnb.location.entity.CityPhoto;
import com.HotelManagement.AirBnb.location.repository.CityPhotoRepository;
import com.HotelManagement.AirBnb.location.repository.LocationRepository;
import com.HotelManagement.AirBnb.search.service.HotelSearchService;
import com.HotelManagement.AirBnb.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** The guest home page's "Explore stays by city": cities with listed hotels, and the city photos. */
@Service
@Transactional(readOnly = true)
public class DestinationService {

    private static final Logger log = LoggerFactory.getLogger(DestinationService.class);

    private final LocationRepository locationRepository;
    private final CityPhotoRepository cityPhotoRepository;

    public DestinationService(LocationRepository locationRepository, CityPhotoRepository cityPhotoRepository) {
        this.locationRepository = locationRepository;
        this.cityPhotoRepository = cityPhotoRepository;
    }

    /** One query for all cities; the image is the city photo, else a hotel photo from the city, else none. */
    public List<DestinationResponse> getDestinations() {
        return locationRepository.findDestinationRows().stream()
                .map(row -> {
                    Long locationId = ((Number) row[0]).longValue();
                    String photoUrl = null;
                    String credit = null;
                    if (row[4] != null) {
                        photoUrl = photoUrl(locationId);
                        credit = credit((String) row[5], (String) row[6]);
                    } else if (row[8] != null) {
                        photoUrl = HotelSearchService.hotelPhotoUrl(((Number) row[7]).longValue(), ((Number) row[8]).longValue());
                    }
                    return new DestinationResponse(locationId, (String) row[1], (String) row[2],
                            ((Number) row[3]).longValue(), photoUrl, credit);
                })
                .toList();
    }

    public CityPhoto getPhoto(Long locationId) {
        log.debug("City photo requested for location {}", locationId);
        return cityPhotoRepository.findByLocation(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));
    }

    static String photoUrl(Long locationId) {
        return "/api/locations/" + locationId + "/photo";
    }

    private static String credit(String artist, String license) {
        String by = artist == null || artist.isBlank() ? "Unknown" : artist;
        return "Photo: " + by + (license == null || license.isBlank() ? "" : " (" + license + ")") + ", via Wikimedia Commons";
    }
}
