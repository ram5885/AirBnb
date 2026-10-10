package com.HotelManagement.AirBnb.repository;

import com.HotelManagement.AirBnb.dto.PhotoMetadata;
import com.HotelManagement.AirBnb.entity.HotelPhoto;
import com.HotelManagement.AirBnb.repository.query.PhotoQueries;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HotelPhotoRepository extends WriteRepository<HotelPhoto, Long> {

    /** Rows of [id, hotel_id, file_name, content_type, size_bytes]; use findMetadataByHotelIds. */
    @Query(value = PhotoQueries.HOTEL_METADATA_BY_HOTEL_IDS, nativeQuery = true)
    List<Object[]> findMetadataRowsByHotelIds(@Param("hotelIds") Collection<Long> hotelIds);

    /** Photo metadata without the image bytes. */
    default List<PhotoMetadata> findMetadataByHotelIds(Collection<Long> hotelIds) {
        return findMetadataRowsByHotelIds(hotelIds).stream().map(PhotoMetadataRows::toMetadata).toList();
    }

    @Query(value = PhotoQueries.FIND_HOTEL_PHOTO, nativeQuery = true)
    Optional<HotelPhoto> findInHotel(@Param("id") Long id, @Param("hotelId") Long hotelId);

    @Query(value = PhotoQueries.COUNT_HOTEL_PHOTOS, nativeQuery = true)
    long countInHotel(@Param("hotelId") Long hotelId);

    @Modifying(flushAutomatically = true)
    @Query(value = PhotoQueries.DELETE_HOTEL_PHOTOS, nativeQuery = true)
    int deleteInHotel(@Param("hotelId") Long hotelId, @Param("ids") Collection<Long> ids);
}
