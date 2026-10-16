package com.HotelManagement.AirBnb.hotel.repository.query;

/** Native SQL for HotelRepository (table hotel). Soft-deleted hotels have deleted_at set. */
public final class HotelQueries {

    private HotelQueries() {
    }

    public static final String COUNT_ALL = """
            SELECT COUNT(*) FROM hotel""";

    public static final String FIND_BY_ID = """
            SELECT h.* FROM hotel h WHERE h.hotel_id = :id""";

    public static final String FIND_OWNED = """
            SELECT h.* FROM hotel h
            WHERE h.hotel_id = :hotelId AND h.owner_id = :ownerId AND h.deleted_at IS NULL""";

    public static final String FIND_OWNED_FOR_UPDATE = FIND_OWNED + "\nFOR UPDATE";

    /** Owner's live hotels, newest first; optional active filter (NULL = all). */
    public static final String OWNER_PAGE_WHERE = """
            FROM hotel h
            WHERE h.owner_id = :ownerId AND h.deleted_at IS NULL
              AND (CAST(:active AS boolean) IS NULL OR h.active = CAST(:active AS boolean))""";

    public static final String FIND_OWNER_PAGE = "SELECT h.* " + OWNER_PAGE_WHERE + "\nORDER BY h.created_at DESC, h.hotel_id DESC";

    public static final String COUNT_OWNER_PAGE = "SELECT COUNT(*) " + OWNER_PAGE_WHERE;

    public static final String COUNT_OWNED_LIVE = """
            SELECT COUNT(*) FROM hotel h WHERE h.owner_id = :ownerId AND h.deleted_at IS NULL""";

    public static final String COUNT_OWNED_ACTIVE = """
            SELECT COUNT(*) FROM hotel h WHERE h.owner_id = :ownerId AND h.active = TRUE AND h.deleted_at IS NULL""";

    /** Includes soft-deleted hotels (the demo seeder must not recreate a hotel the manager deleted). */
    public static final String FIND_FIRST_BY_OWNER_AND_NAME = """
            SELECT h.* FROM hotel h WHERE h.owner_id = :ownerId AND h.name = :name
            ORDER BY h.hotel_id LIMIT 1""";

    /** A hotel guests may see: active and not deleted. */
    public static final String FIND_PUBLIC = """
            SELECT h.* FROM hotel h WHERE h.hotel_id = :id AND h.active = TRUE AND h.deleted_at IS NULL""";

    /**
     * The availability rule shared by guest search and the home page sections: hotel {@code h} has at least
     * one live room type with :rooms free on every night in [checkIn, checkOut). A night is free when its
     * inventory row exists, isn't closed and total_count - booked_count covers the request (booked_count
     * already includes every inventory-holding booking; cancelled and expired ones are released).
     * <p>
     * The optional search filters apply to that same room type, so one bookable option must satisfy all of
     * them: :amenities (comma-separated codes, '' = no filter) must all be offered by the room type or the
     * hotel, and its average nightly price for the stay (sum of the nights' prices / :nights, per room) must
     * be within :minPrice..:maxPrice (NULL = no bound). Prices are compared as totals (bound x :nights) so
     * there is no rounding.
     */
    static final String HAS_BOOKABLE_ROOM = """
            EXISTS (
                SELECT 1 FROM room r
                WHERE r.hotel_id = h.hotel_id AND r.deleted_at IS NULL
                  AND (:amenities = ''
                       OR (COALESCE(r.amenities, CAST('{}' AS text[])) || COALESCE(h.amenities, CAST('{}' AS text[])))
                          @> string_to_array(:amenities, ','))
                  AND (SELECT COUNT(*) = :nights
                              AND (CAST(:minPrice AS numeric) IS NULL OR SUM(i.price) >= CAST(:minPrice AS numeric) * :nights)
                              AND (CAST(:maxPrice AS numeric) IS NULL OR SUM(i.price) <= CAST(:maxPrice AS numeric) * :nights)
                       FROM inventory i
                       WHERE i.hotel_id = h.hotel_id AND i.room_id = r.id
                         AND i.date >= :checkIn AND i.date < :checkOut
                         AND i.closed = FALSE AND i.total_count - i.booked_count >= :rooms))""";

    /**
     * Optional guest-rating filter: the hotel's average review rating, rounded to one decimal as it is shown,
     * is at least :minRating (NULL = no filter). Hotels without reviews have no average, so they never pass
     * an active rating filter. Uses idx_hotel_review_hotel_rating.
     */
    static final String MEETS_MIN_RATING = """
            (CAST(:minRating AS numeric) IS NULL
             OR (SELECT ROUND(AVG(v.rating), 1) FROM hotel_review v WHERE v.hotel_id = h.hotel_id)
                >= CAST(:minRating AS numeric))""";

    /**
     * Guest search: active, non-deleted hotels at the location that can take the stay with the filters
     * (HAS_BOOKABLE_ROOM) and meet the rating filter (MEETS_MIN_RATING).
     */
    public static final String SEARCH_WHERE = """
            FROM hotel h
            WHERE h.location_id = :locationId AND h.active = TRUE AND h.deleted_at IS NULL
              AND\s""" + HAS_BOOKABLE_ROOM + "\n  AND " + MEETS_MIN_RATING;

    public static final String SEARCH = "SELECT h.* " + SEARCH_WHERE + "\nORDER BY h.name, h.hotel_id";

    public static final String SEARCH_COUNT = "SELECT COUNT(*) " + SEARCH_WHERE;

    /**
     * Home page sections: [hotel_id, location_id, available_in_city] for every active, non-deleted hotel that
     * can take the stay, keeping the first :perCity per city by name. available_in_city counts all of the
     * city's matching hotels, before the per-city cut.
     */
    public static final String SECTION_HOTEL_IDS = """
            SELECT x.hotel_id, x.location_id, x.available_in_city
            FROM (SELECT h.hotel_id, h.location_id,
                         ROW_NUMBER() OVER (PARTITION BY h.location_id ORDER BY h.name, h.hotel_id) AS rn,
                         COUNT(*) OVER (PARTITION BY h.location_id) AS available_in_city
                  FROM hotel h
                  WHERE h.active = TRUE AND h.deleted_at IS NULL
                    AND\s""" + HAS_BOOKABLE_ROOM + """
                  ) x
            WHERE x.rn <= :perCity""";

    /** Active, non-deleted hotels with the given ids (any order). */
    public static final String FIND_PUBLIC_BY_IDS = """
            SELECT h.* FROM hotel h WHERE h.hotel_id IN (:ids) AND h.active = TRUE AND h.deleted_at IS NULL""";

    /** Shared row lock held while booking, so a concurrent deactivation/deletion waits. */
    public static final String FIND_BY_ID_FOR_SHARE = FIND_BY_ID + "\nFOR SHARE";
}
