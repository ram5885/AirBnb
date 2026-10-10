package com.HotelManagement.AirBnb.hotel.repository.query;

/**
 * Native SQL for HotelPhotoRepository / RoomPhotoRepository (tables hotel_photo, room_photo).
 * Metadata queries never select the bytea data column.
 */
public final class PhotoQueries {

    private PhotoQueries() {
    }

    /** [id, hotel_id, file_name, content_type, size_bytes] */
    public static final String HOTEL_METADATA_BY_HOTEL_IDS = """
            SELECT p.id, p.hotel_id, p.file_name, p.content_type, p.size_bytes
            FROM hotel_photo p WHERE p.hotel_id IN (:hotelIds) ORDER BY p.id""";

    public static final String FIND_HOTEL_PHOTO = """
            SELECT p.* FROM hotel_photo p WHERE p.id = :id AND p.hotel_id = :hotelId""";

    public static final String COUNT_HOTEL_PHOTOS = """
            SELECT COUNT(*) FROM hotel_photo p WHERE p.hotel_id = :hotelId""";

    public static final String DELETE_HOTEL_PHOTOS = """
            DELETE FROM hotel_photo p WHERE p.hotel_id = :hotelId AND p.id IN (:ids)""";

    /** [id, room_id, file_name, content_type, size_bytes] */
    public static final String ROOM_METADATA_BY_ROOM_IDS = """
            SELECT p.id, p.room_id, p.file_name, p.content_type, p.size_bytes
            FROM room_photo p WHERE p.room_id IN (:roomIds) ORDER BY p.id""";

    public static final String FIND_ROOM_PHOTO = """
            SELECT p.* FROM room_photo p WHERE p.id = :id AND p.room_id = :roomId""";

    public static final String COUNT_ROOM_PHOTOS = """
            SELECT COUNT(*) FROM room_photo p WHERE p.room_id = :roomId""";

    public static final String DELETE_ROOM_PHOTOS = """
            DELETE FROM room_photo p WHERE p.room_id = :roomId AND p.id IN (:ids)""";
}
