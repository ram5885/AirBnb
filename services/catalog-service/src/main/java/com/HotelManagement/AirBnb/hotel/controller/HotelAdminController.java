package com.HotelManagement.AirBnb.hotel.controller;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.HotelManagement.AirBnb.shared.security.CurrentUserService;
import com.HotelManagement.AirBnb.hotel.dto.HotelDetailResponse;
import com.HotelManagement.AirBnb.hotel.dto.HotelRequest;
import com.HotelManagement.AirBnb.hotel.dto.HotelStatusRequest;
import com.HotelManagement.AirBnb.hotel.dto.HotelSummaryResponse;
import com.HotelManagement.AirBnb.hotel.service.HotelService;
import com.HotelManagement.AirBnb.shared.dto.PageResponse;
import com.HotelManagement.AirBnb.shared.web.PhotoResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Hotel management for HOTEL_MANAGER users (role enforced in SecurityConfig, ownership in HotelService). */
@RestController
@RequestMapping("/api/v1/admin")
public class HotelAdminController {

    private static final Logger log = LoggerFactory.getLogger(HotelAdminController.class);

    private final HotelService hotelService;
    private final CurrentUserService currentUser;

    public HotelAdminController(HotelService hotelService, CurrentUserService currentUser) {
        this.hotelService = hotelService;
        this.currentUser = currentUser;
    }

    /**
     * <b>POST /api/v1/admin/hotels</b> (multipart/form-data) — create a hotel owned by the caller.
     * <p>
     * Part {@code data}: JSON with name, state, city, address, landmark, contactPerson, phoneNumber
     * (10-digit Indian mobile), email, amenities (codes from /api/v1/amenities) and active.<br>
     * Part {@code photos}: 0–10 JPEG/PNG/WebP images, max 5 MB each, stored in the database.
     * <p>
     * The city/state pair must exist in the location table. The owner always comes from the token, and
     * createdAt/updatedAt are set by the server.
     * <p>
     * Access: HOTEL_MANAGER.<br>
     * 201 Created: the hotel's full details including photo URLs.<br>
     * 400: invalid fields (with {@code fieldErrors}), unknown city/state or amenity, invalid image.
     * 413: a photo is larger than 5 MB.
     *
     * @param request hotel details (the "data" part)
     * @param photos optional photos (the "photos" parts)
     * @return the created hotel
     */
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(value = "/hotels", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public HotelDetailResponse create(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestPart("data") HotelRequest request,
                                      @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
        log.debug("POST /api/v1/admin/hotels");
        return hotelService.create(currentUser.requireId(jwt), request, photos);
    }

    /**
     * <b>GET /api/v1/admin/hotels</b> — the caller's hotels, newest first.
     * <p>
     * Paginated list of hotel summaries (name, city, state, active flag, cover photo URL, photo and
     * room-type counts, timestamps). Deleted hotels are excluded.
     * <p>
     * Access: HOTEL_MANAGER. 200 OK.
     *
     * @param active optional filter: true = active only, false = inactive only, absent = all
     * @param page zero-based page number (default 0)
     * @param size page size, 1–100 (default 12)
     * @return one page of the caller's hotels
     */
    @GetMapping("/hotels")
    public PageResponse<HotelSummaryResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestParam(required = false) Boolean active,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "12") int size) {
        log.debug("GET /api/v1/admin/hotels active={} page={} size={}", active, page, size);
        return hotelService.list(currentUser.requireId(jwt), active, Math.max(page, 0), PhotoResponses.pageSize(size));
    }

    /**
     * <b>GET /api/v1/admin/hotels/{hotelId}</b> — full details of one of the caller's hotels.
     * <p>
     * Includes location, private contact information (contact person, phone, email, address), amenities,
     * photo URLs, active flag, room-type count and timestamps. Used by the details and edit screens.
     * <p>
     * Access: HOTEL_MANAGER, owner only.<br>
     * 200 OK. 404: no such hotel, deleted, or owned by another manager.
     *
     * @param hotelId hotel id
     * @return the hotel's details
     */
    @GetMapping("/hotels/{hotelId}")
    public HotelDetailResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId) {
        log.debug("GET /api/v1/admin/hotels/{hotelId} hotelId={}", hotelId);
        return hotelService.get(currentUser.requireId(jwt), hotelId);
    }

    /**
     * <b>PUT /api/v1/admin/hotels/{hotelId}</b> (multipart/form-data) — edit a hotel.
     * <p>
     * Replaces all editable fields with the {@code data} part (same fields as create, plus optional
     * {@code removedPhotoIds} to delete existing photos) and appends any new {@code photos}, keeping at
     * most 10 in total. If the city changes, the hotel's inventory rows are updated to match. updatedAt
     * is refreshed automatically.
     * <p>
     * Access: HOTEL_MANAGER, owner only.<br>
     * 200 OK: the updated details. 400: invalid fields, city/state, amenity or image.
     * 404: not the caller's hotel.
     *
     * @param hotelId hotel id
     * @param request new hotel details (the "data" part)
     * @param photos optional new photos to add
     * @return the updated hotel
     */
    @PutMapping(value = "/hotels/{hotelId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public HotelDetailResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId,
                                      @Valid @RequestPart("data") HotelRequest request,
                                      @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
        log.debug("PUT /api/v1/admin/hotels/{hotelId} hotelId={}", hotelId);
        return hotelService.update(currentUser.requireId(jwt), hotelId, request, photos);
    }

    /**
     * <b>PATCH /api/v1/admin/hotels/{hotelId}/status</b> — activate or deactivate a hotel.
     * <p>
     * Body: {@code {"active": true|false}}. An inactive hotel disappears from guest search and its public
     * pages, and new bookings are refused; existing bookings are not affected.
     * <p>
     * Access: HOTEL_MANAGER, owner only.<br>
     * 200 OK: the updated details. 400: missing {@code active}. 404: not the caller's hotel.
     *
     * @param hotelId hotel id
     * @param request the new status
     * @return the updated hotel
     */
    @PatchMapping("/hotels/{hotelId}/status")
    public HotelDetailResponse setStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId,
                                         @Valid @RequestBody HotelStatusRequest request) {
        log.debug("PATCH /api/v1/admin/hotels/{hotelId}/status hotelId={}", hotelId);
        return hotelService.setActive(currentUser.requireId(jwt), hotelId, request.active());
    }

    /**
     * <b>DELETE /api/v1/admin/hotels/{hotelId}</b> — delete (soft delete) a hotel.
     * <p>
     * Marks the hotel and all its room types as deleted and closes their future dates, so it can no
     * longer be seen or booked. Past and cancelled bookings keep pointing at it, so booking history is
     * preserved.
     * <p>
     * Access: HOTEL_MANAGER, owner only.<br>
     * 204 No Content. 404: not the caller's hotel. 409: it still has upcoming bookings that hold rooms;
     * cancel them or deactivate the hotel instead.
     *
     * @param hotelId hotel id
     */
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/hotels/{hotelId}")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId) {
        log.debug("DELETE /api/v1/admin/hotels/{hotelId} hotelId={}", hotelId);
        hotelService.delete(currentUser.requireId(jwt), hotelId);
    }

    /**
     * <b>GET /api/v1/admin/hotels/{hotelId}/photos/{photoId}</b> — one hotel photo's image bytes.
     * <p>
     * Streams the stored image with its content type (JPEG/PNG/WebP) and a private, 1-hour cache header.
     * Unlike the public photo URL, this works for inactive hotels too, so managers always see their photos.
     * <p>
     * Access: HOTEL_MANAGER, owner only.<br>
     * 200 OK (image). 404: unknown photo or not the caller's hotel.
     *
     * @param hotelId hotel id
     * @param photoId photo id
     * @return the image
     */
    @GetMapping("/hotels/{hotelId}/photos/{photoId}")
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId,
                                        @PathVariable Long photoId) {
        log.debug("GET /api/v1/admin/hotels/{hotelId}/photos/{photoId} hotelId={} photoId={}", hotelId, photoId);
        return PhotoResponses.of(hotelService.getPhoto(currentUser.requireId(jwt), hotelId, photoId));
    }
}
