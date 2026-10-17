package com.HotelManagement.AirBnb.booking.repository.query;

/**
 * Native SQL for BookingRepository (table booking; booking_guest links guests).
 * Statuses are stored as their enum names. Optional filters use CAST(:param AS type) IS NULL so a
 * NULL parameter means "no filter".
 */
public final class BookingQueries {

    private BookingQueries() {
    }

    public static final String COUNT_ALL = """
            SELECT COUNT(*) FROM booking""";

    public static final String FIND_BY_ID_FOR_UPDATE = """
            SELECT b.* FROM booking b WHERE b.booking_id = :id
            FOR UPDATE""";

    // ---- guest side (owner = booking's user)

    public static final String FIND_FOR_USER = """
            SELECT b.* FROM booking b WHERE b.booking_id = :id AND b.user_id = :userId""";

    public static final String FIND_FOR_USER_FOR_UPDATE = FIND_FOR_USER + "\nFOR UPDATE";

    /** The user's CONFIRMED bookings at one hotel, latest check-out first (candidates for a review). */
    public static final String FIND_CONFIRMED_FOR_USER_AT_HOTEL = """
            SELECT b.* FROM booking b
            WHERE b.user_id = :userId AND b.hotel_id = :hotelId AND b.booking_status = 'CONFIRMED'
            ORDER BY b.check_out_date DESC, b.booking_id DESC""";

    /** My Bookings page: statuses plus optional check-out bounds; order by check-in or by newest. */
    public static final String USER_PAGE_WHERE = """
            FROM booking b
            WHERE b.user_id = :userId AND b.booking_status IN (:statuses)
              AND (CAST(:checkoutAfter AS date) IS NULL OR b.check_out_date > CAST(:checkoutAfter AS date))
              AND (CAST(:checkoutOnOrBefore AS date) IS NULL OR b.check_out_date <= CAST(:checkoutOnOrBefore AS date))""";

    public static final String FIND_USER_PAGE = "SELECT b.* " + USER_PAGE_WHERE + """

            ORDER BY CASE WHEN :orderByCheckIn THEN b.check_in_date END ASC, b.created_at DESC, b.booking_id DESC""";

    public static final String COUNT_USER_PAGE = "SELECT COUNT(*) " + USER_PAGE_WHERE;

    // ---- hotel manager side (owner = hotel's owner, kept on the booking as hotel_owner_id)

    public static final String FIND_FOR_OWNER = """
            SELECT b.* FROM booking b
            WHERE b.booking_id = :id AND b.hotel_owner_id = :ownerId""";

    public static final String FIND_FOR_OWNER_FOR_UPDATE = FIND_FOR_OWNER + "\nFOR UPDATE";

    /** Manager list filters (all optional): hotel, status, stays overlapping [startDate, endDate], booking id. */
    public static final String OWNER_PAGE_WHERE = """
            FROM booking b
            WHERE b.hotel_owner_id = :ownerId
              AND (CAST(:hotelId AS bigint) IS NULL OR b.hotel_id = CAST(:hotelId AS bigint))
              AND (CAST(:status AS varchar) IS NULL OR b.booking_status = CAST(:status AS varchar))
              AND (CAST(:startDate AS date) IS NULL OR b.check_out_date > CAST(:startDate AS date))
              AND (CAST(:endDate AS date) IS NULL OR b.check_in_date <= CAST(:endDate AS date))
              AND (CAST(:bookingId AS bigint) IS NULL OR b.booking_id = CAST(:bookingId AS bigint))""";

    public static final String FIND_OWNER_PAGE = "SELECT b.* " + OWNER_PAGE_WHERE + "\nORDER BY b.created_at DESC, b.booking_id DESC";

    public static final String COUNT_OWNER_PAGE = "SELECT COUNT(*) " + OWNER_PAGE_WHERE;

    /** [booking_status, count] for the owner's hotels. */
    public static final String COUNT_BY_STATUS_FOR_OWNER = """
            SELECT b.booking_status, COUNT(*) FROM booking b
            WHERE b.hotel_owner_id = :ownerId GROUP BY b.booking_status""";

    // ---- shared

    /** Unpaid holds older than the cutoff (expiry candidates). */
    public static final String FIND_STALE_HOLD_IDS = """
            SELECT b.booking_id FROM booking b WHERE b.booking_status = 'RESERVED' AND b.created_at < :cutoff""";

    public static final String FIND_BY_HOLD_KEY = """
            SELECT b.* FROM booking b WHERE b.hold_key = :holdKey""";

    /** Cancelled/expired bookings whose rooms catalog hasn't confirmed as released yet (retry candidates). */
    public static final String FIND_RELEASE_PENDING_IDS = """
            SELECT b.booking_id FROM booking b
            WHERE b.hold_status = 'RELEASE_PENDING' AND b.updated_at < :before
            ORDER BY b.booking_id LIMIT 200""";

    /** [booking_id, guest count] for a page of bookings. */
    public static final String COUNT_GUESTS = """
            SELECT b.booking_id, COUNT(bg.guest_id) FROM booking b
            LEFT JOIN booking_guest bg ON bg.booking_id = b.booking_id
            WHERE b.booking_id IN (:ids) GROUP BY b.booking_id""";
}
