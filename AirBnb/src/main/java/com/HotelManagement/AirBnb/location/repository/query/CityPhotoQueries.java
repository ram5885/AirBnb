package com.HotelManagement.AirBnb.location.repository.query;

/** Native SQL for CityPhotoRepository (table city_photo). */
public final class CityPhotoQueries {

    private CityPhotoQueries() {
    }

    public static final String FIND_BY_LOCATION = """
            SELECT p.* FROM city_photo p WHERE p.location_id = :locationId""";

    public static final String FIND_PHOTOGRAPHED_LOCATION_IDS = """
            SELECT p.location_id FROM city_photo p""";

}
