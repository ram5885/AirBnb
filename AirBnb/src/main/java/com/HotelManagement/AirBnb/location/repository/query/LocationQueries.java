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
}
