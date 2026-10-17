package com.HotelManagement.AirBnb.location.config;

import com.HotelManagement.AirBnb.shared.csv.CsvTable;
import com.HotelManagement.AirBnb.hotel.service.ImageValidator;
import com.HotelManagement.AirBnb.location.entity.CityPhoto;
import com.HotelManagement.AirBnb.location.entity.Location;
import com.HotelManagement.AirBnb.location.repository.CityPhotoRepository;
import com.HotelManagement.AirBnb.location.repository.LocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stores each city's iconic photo in the {@code city_photo} table on startup, from the images bundled
 * under {@code data/city-photos/} and their manifest {@code data/city-photos.csv} (city, state, file,
 * source_page, image_page, artist, license). Both are produced by {@code scripts/fetch_city_photos.py}
 * from Wikipedia / Wikimedia Commons.
 * <p>
 * Runs after LocationDataLoader. Cities that already have a photo, unknown cities and missing or
 * non-image files are skipped, so it is safe to run on every startup.
 */
@Component
@Order(1)
public class CityPhotoLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CityPhotoLoader.class);

    private final LocationRepository locationRepository;
    private final CityPhotoRepository cityPhotoRepository;
    private final Resource manifest;

    public CityPhotoLoader(LocationRepository locationRepository, CityPhotoRepository cityPhotoRepository,
                           @Value("${app.city-photos.csv:classpath:data/city-photos.csv}") Resource manifest) {
        this.locationRepository = locationRepository;
        this.cityPhotoRepository = cityPhotoRepository;
        this.manifest = manifest;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (!manifest.exists()) {
            log.warn("City photo manifest not found at {}; skipping import", manifest);
            return;
        }
        List<CsvTable.Row> rows = CsvTable.read(manifest, "city", "state", "file");
        Set<Long> photographed = new HashSet<>(cityPhotoRepository.findPhotographedLocationIds());
        int inserted = 0, existing = 0, skipped = 0;

        for (CsvTable.Row row : rows) {
            Location location = findLocation(row.get("city"), row.get("state"));
            if (location == null) {
                skipped++;
                log.warn("Skipping city photo at {}: unknown city {}, {}", row.where(), row.get("city"), row.get("state"));
                continue;
            }
            if (!photographed.add(location.getId())) {
                existing++;
                continue;
            }
            String file = row.get("file");
            Resource image = manifest.createRelative("city-photos/" + file);
            byte[] bytes = image.exists() && !file.contains("/") && !file.contains("..") ? image.getContentAsByteArray() : null;
            String contentType = bytes == null ? null : ImageValidator.detectType(bytes);
            if (contentType == null) {
                skipped++;
                log.warn("Skipping city photo at {}: '{}' is missing or not an image", row.where(), file);
                continue;
            }
            CityPhoto photo = new CityPhoto();
            photo.setLocation(location);
            photo.setFileName(file);
            photo.setContentType(contentType);
            photo.setSizeBytes((long) bytes.length);
            photo.setData(bytes);
            photo.setSourcePage(limit(row.get("source_page"), 500));
            photo.setImagePage(limit(row.get("image_page"), 500));
            photo.setArtist(limit(row.get("artist"), 200));
            photo.setLicense(limit(row.get("license"), 100));
            cityPhotoRepository.save(photo);
            inserted++;
        }
        log.info("City photo import: {} rows, {} inserted, {} already stored, {} skipped", rows.size(), inserted, existing, skipped);
    }

    /** Matches the state the way LocationDataLoader normalises it ("WestBengal" is "West Bengal"). */
    private Location findLocation(String city, String state) {
        String key = LocationDataLoader.compactKey(state);
        return locationRepository.findByCity(city).stream()
                .filter(l -> LocationDataLoader.compactKey(l.getState()).equals(key))
                .findFirst().orElse(null);
    }

    private static String limit(String value, int max) {
        return value.isEmpty() ? null : value.length() > max ? value.substring(0, max) : value;
    }
}
