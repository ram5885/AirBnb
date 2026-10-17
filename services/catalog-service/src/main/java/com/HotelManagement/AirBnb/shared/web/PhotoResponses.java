package com.HotelManagement.AirBnb.shared.web;

import com.HotelManagement.AirBnb.hotel.entity.PhotoBase;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

public final class PhotoResponses {

    private PhotoResponses() {
    }

    /** Streams stored image bytes with the detected (trusted) content type; private, cacheable. */
    public static ResponseEntity<byte[]> of(PhotoBase photo) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .contentLength(photo.getSizeBytes())
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(photo.getData());
    }

    public static int pageSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
