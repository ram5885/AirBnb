package com.HotelManagement.AirBnb.location.repository.query;

/** Native SQL for LocationRepository (table location). */
public final class LocationQueries {

    private LocationQueries() {
    }

    public static final String FIND_ALL = """
            SELECT l.* FROM location l""";

    public static final String FIND_ALL_ORDERED = """
            SELECT l.* FROM location l ORDER BY l.state, l.city""";

    public static final String FIND_BY_STATE = """
            SELECT l.* FROM location l WHERE LOWER(l.state) = LOWER(:state) ORDER BY l.city""";

    public static final String FIND_BY_CITY_AND_STATE = """
            SELECT l.* FROM location l
            WHERE LOWER(l.city) = LOWER(:city) AND LOWER(l.state) = LOWER(:state)""";

    /** City names repeat across states, so this can return several rows. */
    public static final String FIND_BY_CITY = """
            SELECT l.* FROM location l WHERE LOWER(l.city) = LOWER(:city) ORDER BY l.state""";

    public static final String FIND_DISTINCT_STATES = """
            SELECT DISTINCT l.state FROM location l ORDER BY l.state""";

    public static final String COUNT_ALL = """
            SELECT COUNT(*) FROM location""";

    /**
     * "Explore stays by city": [location_id, city, state, hotel_count, city_photo_id, artist, license,
     * hotel_photo_hotel_id, hotel_photo_id] for every city with at least one listed hotel, i.e. active, not
     * deleted and with at least one live room type (availability for particular dates is not required).
     * When the city has no city photo, hotel_photo_* names a photo of one of those hotels (lowest hotel id,
     * then photo id), if any. Most hotels first, then by city.
     */
    public static final String DESTINATIONS = """
            WITH listed AS (
                SELECT h.hotel_id, h.location_id FROM hotel h
                WHERE h.active = TRUE AND h.deleted_at IS NULL
                  AND EXISTS (SELECT 1 FROM room r WHERE r.hotel_id = h.hotel_id AND r.deleted_at IS NULL))
            SELECT l.id, l.city, l.state, c.hotel_count, p.id AS city_photo_id, p.artist, p.license,
                   hp.hotel_id AS hotel_photo_hotel_id, hp.id AS hotel_photo_id
            FROM (SELECT location_id, COUNT(*) AS hotel_count FROM listed GROUP BY location_id) c
            JOIN location l ON l.id = c.location_id
            LEFT JOIN city_photo p ON p.location_id = l.id
            LEFT JOIN LATERAL (
                SELECT ph.hotel_id, ph.id FROM hotel_photo ph JOIN listed x ON x.hotel_id = ph.hotel_id
                WHERE x.location_id = l.id
                ORDER BY ph.hotel_id, ph.id LIMIT 1) hp ON p.id IS NULL
            ORDER BY c.hotel_count DESC, l.city""";
}
