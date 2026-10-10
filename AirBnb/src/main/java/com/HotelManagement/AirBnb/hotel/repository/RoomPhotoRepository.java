package com.HotelManagement.AirBnb.repository;

import com.HotelManagement.AirBnb.dto.PhotoMetadata;
import com.HotelManagement.AirBnb.entity.RoomPhoto;
import com.HotelManagement.AirBnb.repository.query.PhotoQueries;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomPhotoRepository extends WriteRepository<RoomPhoto, Long> {

    /** Rows of [id, room_id, file_name, content_type, size_bytes]; use findMetadataByRoomIds. */
    @Query(value = PhotoQueries.ROOM_METADATA_BY_ROOM_IDS, nativeQuery = true)
    List<Object[]> findMetadataRowsByRoomIds(@Param("roomIds") Collection<Long> roomIds);

    /** Photo metadata without the image bytes. */
    default List<PhotoMetadata> findMetadataByRoomIds(Collection<Long> roomIds) {
        return findMetadataRowsByRoomIds(roomIds).stream().map(PhotoMetadataRows::toMetadata).toList();
    }

    @Query(value = PhotoQueries.FIND_ROOM_PHOTO, nativeQuery = true)
    Optional<RoomPhoto> findInRoom(@Param("id") Long id, @Param("roomId") Long roomId);

    @Query(value = PhotoQueries.COUNT_ROOM_PHOTOS, nativeQuery = true)
    long countInRoom(@Param("roomId") Long roomId);

    @Modifying(flushAutomatically = true)
    @Query(value = PhotoQueries.DELETE_ROOM_PHOTOS, nativeQuery = true)
    int deleteInRoom(@Param("roomId") Long roomId, @Param("ids") Collection<Long> ids);
}
