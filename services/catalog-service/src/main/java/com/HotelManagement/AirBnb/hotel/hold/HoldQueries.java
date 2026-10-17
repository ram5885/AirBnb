package com.HotelManagement.AirBnb.hotel.hold;

/** Native SQL for InventoryHoldRepository (table inventory_hold). */
final class HoldQueries {

    private HoldQueries() {
    }

    static final String FIND_BY_KEY = """
            SELECT h.* FROM inventory_hold h WHERE h.hold_key = :holdKey""";

    static final String FIND_BY_KEY_FOR_UPDATE = FIND_BY_KEY + "\nFOR UPDATE";

    static final String FIND_UNATTACHED_BEFORE = """
            SELECT h.* FROM inventory_hold h
            WHERE h.booking_id IS NULL AND h.status = 'HELD' AND h.created_at < :cutoff
            ORDER BY h.id LIMIT 500""";

    static final String EXISTS_UPCOMING_FOR_HOTEL = """
            SELECT EXISTS (SELECT 1 FROM inventory_hold h
                           WHERE h.hotel_id = :hotelId AND h.status = 'HELD' AND h.check_out_date > :today)""";

    static final String EXISTS_UPCOMING_FOR_ROOM = """
            SELECT EXISTS (SELECT 1 FROM inventory_hold h
                           WHERE h.room_id = :roomId AND h.status = 'HELD' AND h.check_out_date > :today)""";
}
