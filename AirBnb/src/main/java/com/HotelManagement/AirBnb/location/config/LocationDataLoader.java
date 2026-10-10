package com.HotelManagement.AirBnb.location.config;

import com.HotelManagement.AirBnb.location.entity.Location;
import com.HotelManagement.AirBnb.location.repository.LocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Seeds the {@code location} table from a CSV of Indian cities and states on startup.
 * <p>
 * The CSV is mapped by header name ("City", "State", case-insensitive), so column
 * order and extra columns (e.g. the source "id") don't matter. Values are trimmed and
 * whitespace-collapsed, state names are normalized to their official spelling
 * (e.g. "WestBengal" -> "West Bengal"), blank/malformed rows are skipped, and
 * duplicate city-state pairs - within the file or already in the DB - are skipped
 * case-insensitively. Safe to run on every startup.
 */
@Component
public class LocationDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocationDataLoader.class);

    private static final int MAX_LENGTH = 100;

    private static final List<String> STATES_AND_UTS = List.of(
            "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Goa", "Gujarat",
            "Haryana", "Himachal Pradesh", "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh",
            "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab", "Rajasthan",
            "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal",
            "Andaman and Nicobar Islands", "Chandigarh", "Dadra and Nagar Haveli and Daman and Diu",
            "Delhi", "Jammu and Kashmir", "Ladakh", "Lakshadweep", "Puducherry");

    private static final Map<String, String> CANONICAL_STATES = new HashMap<>();

    static {
        STATES_AND_UTS.forEach(s -> CANONICAL_STATES.put(compactKey(s), s));
    }

    private final LocationRepository locationRepository;
    private final Resource csv;

    public LocationDataLoader(LocationRepository locationRepository,
                              @Value("${app.locations.csv:classpath:data/locations.csv}") Resource csv) {
        this.locationRepository = locationRepository;
        this.csv = csv;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (!csv.exists()) {
            log.warn("Location dataset not found at {}; skipping import", csv);
            return;
        }

        Set<String> seen = new HashSet<>();
        locationRepository.findAllLocations().forEach(l -> seen.add(dedupKey(l.getCity(), l.getState())));
        int alreadyInDb = seen.size();

        List<Location> toInsert = new ArrayList<>();
        int rows = 0, invalid = 0, duplicates = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csv.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                log.warn("Location dataset {} is empty; skipping import", csv);
                return;
            }
            List<String> header = parseCsvLine(headerLine.replace("﻿", ""));
            int cityIdx = indexOfIgnoreCase(header, "city");
            int stateIdx = indexOfIgnoreCase(header, "state");
            if (cityIdx < 0 || stateIdx < 0) {
                log.error("Location dataset {} must have 'City' and 'State' columns, found {}; skipping import", csv, header);
                return;
            }

            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                rows++;
                List<String> fields = parseCsvLine(line);
                String city = fields.size() > cityIdx ? clean(fields.get(cityIdx)) : "";
                String state = fields.size() > stateIdx ? normalizeState(clean(fields.get(stateIdx))) : "";

                if (city.isEmpty() || state.isEmpty() || city.length() > MAX_LENGTH || state.length() > MAX_LENGTH) {
                    invalid++;
                    log.warn("Skipping invalid location at line {}: {}", lineNo, line);
                    continue;
                }
                if (!seen.add(dedupKey(city, state))) {
                    duplicates++;
                    log.debug("Skipping duplicate location at line {}: {}, {}", lineNo, city, state);
                    continue;
                }
                toInsert.add(new Location(city, state));
            }
        }

        locationRepository.saveAll(toInsert);
        log.info("Location import from {}: {} rows read, {} inserted, {} duplicates skipped, {} invalid skipped ({} already in DB)",
                csv.getFilename(), rows, toInsert.size(), duplicates, invalid, alreadyInDb);
    }

    private static String normalizeState(String state) {
        String canonical = CANONICAL_STATES.get(compactKey(state));
        if (canonical != null) {
            return canonical;
        }
        if (!state.isEmpty()) {
            log.warn("Unrecognized state '{}'; importing as-is", state);
        }
        return state;
    }

    /** "WestBengal", "west bengal" and "West  Bengal" all map to "westbengal". */
    private static String compactKey(String value) {
        return value.toLowerCase(Locale.ROOT).replace("&", "and").replaceAll("[^a-z]", "");
    }

    private static String dedupKey(String city, String state) {
        return city.toLowerCase(Locale.ROOT) + "|" + state.toLowerCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }

    private static int indexOfIgnoreCase(List<String> header, String name) {
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).strip().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    /** Minimal RFC 4180 field splitting: supports quoted fields with commas and "" escapes. */
    private static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    inQuotes = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
