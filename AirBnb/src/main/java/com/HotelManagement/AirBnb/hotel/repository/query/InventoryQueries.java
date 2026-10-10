package com.HotelManagement.AirBnb.repository.query;

/** Native SQL for InventoryRepository (table inventory: one row per room type per date). */
public final class InventoryQueries {

    private InventoryQueries() {
    }

    /** Rows for [start, end) of one room type, in date order. */
    public static final String FIND_RANGE = """
            SELECT i.* FROM inventory i
            WHERE i.room_id = :roomId AND i.date >= :start AND i.date < :end
            ORDER BY i.date""";

    /** Same rows locked FOR UPDATE, always in date order so concurrent writers can't deadlock. */
    public static final String FIND_RANGE_FOR_UPDATE = FIND_RANGE + "\nFOR UPDATE";

    /**
     * Per live room type of the given hotels over the open nights in [start, end):
     * [hotel_id, room_id, open_nights, SUM(price), MIN(total_count - booked_count)].
     */
    public static final String AVAILABILITY_BY_ROOM = """
            SELECT i.hotel_id, i.room_id, COUNT(*), SUM(i.price), MIN(i.total_count - i.booked_count)
            FROM inventory i JOIN room r ON r.id = i.room_id
            WHERE i.hotel_id IN (:hotelIds) AND r.deleted_at IS NULL
              AND i.date >= :start AND i.date < :end AND i.closed = FALSE
            GROUP BY i.hotel_id, i.room_id""";

    public static final String CLOSE_ROOM_FROM = """
            UPDATE inventory SET closed = TRUE WHERE room_id = :roomId AND date >= :from""";

    public static final String CLOSE_HOTEL_FROM = """
            UPDATE inventory SET closed = TRUE WHERE hotel_id = :hotelId AND date >= :from""";

    public static final String UPDATE_CITY_FOR_HOTEL = """
            UPDATE inventory SET city = :city WHERE hotel_id = :hotelId""";

    /** One row per day in [start, end) for a new room type, in a single statement. */
    public static final String GENERATE = """
            INSERT INTO inventory (hotel_id, room_id, date, booked_count, total_count, surge_factor, price, city, closed,
                                   created_at, updated_at)
            SELECT :hotelId, :roomId, d::date, 0, :totalCount, 1.00, :price, :city, FALSE, NOW(), NOW()
            FROM generate_series(CAST(:start AS date), CAST(:end AS date) - 1, INTERVAL '1 day') AS d""";
}
