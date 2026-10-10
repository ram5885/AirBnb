package com.HotelManagement.AirBnb.repository;

import com.HotelManagement.AirBnb.dto.PhotoMetadata;

/** Maps native [id, owner_id, file_name, content_type, size_bytes] rows to PhotoMetadata. */
final class PhotoMetadataRows {

    private PhotoMetadataRows() {
    }

    static PhotoMetadata toMetadata(Object[] row) {
        return new PhotoMetadata(((Number) row[0]).longValue(), ((Number) row[1]).longValue(), (String) row[2],
                (String) row[3], ((Number) row[4]).longValue());
    }
}
