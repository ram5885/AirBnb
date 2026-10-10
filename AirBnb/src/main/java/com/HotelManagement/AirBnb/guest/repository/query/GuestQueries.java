package com.HotelManagement.AirBnb.guest.repository.query;

/** Native SQL for GuestRepository (table guest: people travelling on a user's bookings). */
public final class GuestQueries {

    private GuestQueries() {
    }

    public static final String FIND_BY_USER = """
            SELECT g.* FROM guest g WHERE g.user_id = :userId ORDER BY g.id""";

    public static final String FIND_BY_IDS_FOR_USER = """
            SELECT g.* FROM guest g WHERE g.id IN (:ids) AND g.user_id = :userId""";

    public static final String FIND_BY_ID_FOR_USER = """
            SELECT g.* FROM guest g WHERE g.id = :id AND g.user_id = :userId""";
}
