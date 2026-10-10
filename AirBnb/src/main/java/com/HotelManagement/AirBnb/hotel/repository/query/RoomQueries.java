package com.HotelManagement.AirBnb.repository.query;

/** Native SQL for RoomRepository (table room = room types). */
public final class RoomQueries {

    private RoomQueries() {
    }

    public static final String COUNT_ALL = """
            SELECT COUNT(*) FROM room""";

    public static final String FIND_LIVE_BY_HOTEL = """
            SELECT r.* FROM room r WHERE r.hotel_id = :hotelId AND r.deleted_at IS NULL
            ORDER BY r.created_at, r.id""";

    public static final String FIND_LIVE_IN_HOTEL = """
            SELECT r.* FROM room r WHERE r.id = :id AND r.hotel_id = :hotelId AND r.deleted_at IS NULL""";

    public static final String FIND_LIVE_IN_HOTEL_FOR_UPDATE = FIND_LIVE_IN_HOTEL + "\nFOR UPDATE";

    /** Shared lock held while booking, so a concurrent room-type delete waits. */
    public static final String FIND_IN_HOTEL_FOR_SHARE = """
            SELECT r.* FROM room r WHERE r.id = :id AND r.hotel_id = :hotelId
            FOR SHARE""";

    /** Includes soft-deleted room types (demo seeder). */
    public static final String EXISTS_TYPE_IN_HOTEL = """
            SELECT EXISTS (SELECT 1 FROM room r WHERE r.hotel_id = :hotelId AND LOWER(r.type) = LOWER(:type))""";

    public static final String EXISTS_LIVE_TYPE_IN_HOTEL = """
            SELECT EXISTS (SELECT 1 FROM room r
                           WHERE r.hotel_id = :hotelId AND LOWER(r.type) = LOWER(:type) AND r.deleted_at IS NULL)""";

    public static final String EXISTS_OTHER_LIVE_TYPE_IN_HOTEL = """
            SELECT EXISTS (SELECT 1 FROM room r
                           WHERE r.hotel_id = :hotelId AND LOWER(r.type) = LOWER(:type) AND r.deleted_at IS NULL
                             AND r.id <> :excludeId)""";

    /** [hotel_id, live room type count] per hotel. */
    public static final String COUNT_LIVE_BY_HOTEL_IDS = """
            SELECT r.hotel_id, COUNT(*) FROM room r
            WHERE r.hotel_id IN (:hotelIds) AND r.deleted_at IS NULL
            GROUP BY r.hotel_id""";
}
