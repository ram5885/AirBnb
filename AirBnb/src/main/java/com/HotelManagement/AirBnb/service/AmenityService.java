package com.HotelManagement.AirBnb.service;

import com.HotelManagement.AirBnb.dto.AmenityCategoryResponse;
import com.HotelManagement.AirBnb.dto.AmenityCategoryResponse.AmenityOption;
import com.HotelManagement.AirBnb.entity.enums.Amenity;
import com.HotelManagement.AirBnb.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AmenityService {

    public List<AmenityCategoryResponse> catalog() {
        return Arrays.stream(Amenity.Category.values())
                .map(c -> new AmenityCategoryResponse(c.name(), c.getLabel(),
                        Arrays.stream(Amenity.values())
                                .filter(a -> a.getCategory() == c)
                                .map(a -> new AmenityOption(a.name(), a.getLabel()))
                                .toList()))
                .toList();
    }

    /** Validates codes against the catalog and returns them de-duplicated in catalog order. */
    public String[] normalize(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return new String[0];
        }
        Set<Amenity> selected = new LinkedHashSet<>();
        for (String code : codes) {
            try {
                selected.add(Amenity.valueOf(code));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new BadRequestException("Unknown amenity: " + code);
            }
        }
        return Arrays.stream(Amenity.values()).filter(selected::contains).map(Enum::name).toArray(String[]::new);
    }

    public List<String> toList(String[] stored) {
        return stored == null ? List.of() : List.of(stored);
    }
}
