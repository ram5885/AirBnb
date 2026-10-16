package com.HotelManagement.AirBnb.review.repository.query;

/** Native SQL for HotelReviewRepository (table hotel_review). */
public final class ReviewQueries {

    private ReviewQueries() {
    }

    /** Public review cards: only the reviewer's name is read from app_user (never email or other details). */
    private static final String PAGE_FROM = """
            FROM hotel_review v
            JOIN app_user u ON u.id = v.user_id
            JOIN booking b ON b.booking_id = v.booking_id
            JOIN room r ON r.id = b.room_id
            WHERE v.hotel_id = :hotelId""";

    /** [id, rating, comment, created_at, reviewer_name, check_in_date, room_type] */
    private static final String PAGE_SELECT = """
            SELECT v.id, v.rating, v.comment, v.created_at, u.name, b.check_in_date, r.type
            """ + PAGE_FROM;

    public static final String PAGE_RECENT = PAGE_SELECT + "\nORDER BY v.created_at DESC, v.id DESC";

    public static final String PAGE_HIGHEST = PAGE_SELECT + "\nORDER BY v.rating DESC, v.created_at DESC, v.id DESC";

    public static final String PAGE_LOWEST = PAGE_SELECT + "\nORDER BY v.rating ASC, v.created_at DESC, v.id DESC";

    public static final String COUNT_PAGE = "SELECT COUNT(*) " + PAGE_FROM;

    /** [rating, count] for the hotel's reviews; ratings with no reviews are absent. */
    public static final String RATING_COUNTS = """
            SELECT v.rating, COUNT(*) FROM hotel_review v WHERE v.hotel_id = :hotelId GROUP BY v.rating""";

    /** [booking_id, id, rating, comment, created_at] of the reviews written for these bookings. */
    public static final String BY_BOOKING_IDS = """
            SELECT v.booking_id, v.id, v.rating, v.comment, v.created_at
            FROM hotel_review v WHERE v.booking_id IN (:bookingIds)""";

    public static final String EXISTS_FOR_BOOKING = """
            SELECT EXISTS (SELECT 1 FROM hotel_review v WHERE v.booking_id = :bookingId)""";

    /** [hotel_id, average rounded to one decimal, count] for hotels with reviews (others are absent). */
    public static final String RATINGS_BY_HOTEL_IDS = """
            SELECT v.hotel_id, ROUND(AVG(v.rating), 1), COUNT(*)
            FROM hotel_review v WHERE v.hotel_id IN (:hotelIds) GROUP BY v.hotel_id""";
}
