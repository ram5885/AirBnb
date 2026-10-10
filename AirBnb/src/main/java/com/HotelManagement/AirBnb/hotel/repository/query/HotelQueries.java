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
     * Guest search: active, non-deleted hotels at the location with at least one live room type that has
     * :rooms free on every night in [checkIn, checkOut). A night is free when its inventory row exists,
     * isn't closed and total_count - booked_count covers the request.
     */
    public static final String SEARCH_WHERE = """
            FROM hotel h
            WHERE h.location_id = :locationId AND h.active = TRUE AND h.deleted_at IS NULL
              AND EXISTS (
                SELECT 1 FROM room r
                WHERE r.hotel_id = h.hotel_id AND r.deleted_at IS NULL
                  AND (SELECT COUNT(*) FROM inventory i
                       WHERE i.hotel_id = h.hotel_id AND i.room_id = r.id
                         AND i.date >= :checkIn AND i.date < :checkOut
                         AND i.closed = FALSE AND i.total_count - i.booked_count >= :rooms) = :nights)""";

    public static final String SEARCH = "SELECT h.* " + SEARCH_WHERE + "\nORDER BY h.name, h.hotel_id";

    public static final String SEARCH_COUNT = "SELECT COUNT(*) " + SEARCH_WHERE;

    /** Shared row lock held while booking, so a concurrent deactivation/deletion waits. */
    public static final String FIND_BY_ID_FOR_SHARE = FIND_BY_ID + "\nFOR SHARE";
}
