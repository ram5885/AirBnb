package com.HotelManagement.AirBnb.hotel.controller;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.HotelManagement.AirBnb.shared.security.CurrentUserService;
import com.HotelManagement.AirBnb.hotel.dto.InventoryDayResponse;
import com.HotelManagement.AirBnb.hotel.dto.InventoryUpdateRequest;
import com.HotelManagement.AirBnb.hotel.dto.InventoryUpdateResponse;
import com.HotelManagement.AirBnb.hotel.dto.RoomRequest;
import com.HotelManagement.AirBnb.hotel.dto.RoomResponse;
import com.HotelManagement.AirBnb.hotel.service.InventoryService;
import com.HotelManagement.AirBnb.hotel.service.RoomService;
import com.HotelManagement.AirBnb.shared.web.PhotoResponses;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

/** Room types ("rooms" in the API, per the design doc) and their date-wise inventory. */
@RestController
@RequestMapping("/api/v1/admin/hotels/{hotelId}/rooms")
public class RoomAdminController {

    private static final Logger log = LoggerFactory.getLogger(RoomAdminController.class);

    private final RoomService roomService;
    private final InventoryService inventoryService;
    private final CurrentUserService currentUser;

    public RoomAdminController(RoomService roomService, InventoryService inventoryService, CurrentUserService currentUser) {
        this.roomService = roomService;
        this.inventoryService = inventoryService;
        this.currentUser = currentUser;
    }

    /**
     * <b>POST /api/v1/admin/hotels/{hotelId}/rooms</b> (multipart/form-data) — create a room type.
     * <p>
     * Part {@code data}: JSON with type (name, unique within the hotel), basePrice (per night),
     * totalCount (number of physical rooms of this type, 1–500), capacity (guests per room, 1–20) and
     * amenities. Part {@code photos}: 0–10 images, as for hotels.
     * <p>
     * Also opens availability for the next 365 days: one inventory row per date with totalCount rooms at
     * the base price.
     * <p>
     * Access: HOTEL_MANAGER, owner of the hotel.<br>
     * 201 Created: the room type. 400: invalid fields or image. 404: not the caller's hotel.
     * 409: a room type with that name already exists in the hotel.
     *
     * @param hotelId hotel id
     * @param request room type details (the "data" part)
     * @param photos optional photos
     * @return the created room type
     */
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RoomResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId,
                               @Valid @RequestPart("data") RoomRequest request,
                               @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
        log.debug("POST /api/v1/admin/hotels/{hotelId}/rooms hotelId={}", hotelId);
        return roomService.create(currentUser.requireId(jwt), hotelId, request, photos);
    }

    /**
     * <b>GET /api/v1/admin/hotels/{hotelId}/rooms</b> — the hotel's room types.
     * <p>
     * All live (not deleted) room types of the hotel, oldest first, with price, room count, capacity,
     * amenities and photo URLs.
     * <p>
     * Access: HOTEL_MANAGER, owner of the hotel.<br>
     * 200 OK. 404: not the caller's hotel.
     *
     * @param hotelId hotel id
     * @return the room types
     */
    @GetMapping
    public List<RoomResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId) {
        log.debug("GET /api/v1/admin/hotels/{hotelId}/rooms hotelId={}", hotelId);
        return roomService.list(currentUser.requireId(jwt), hotelId);
    }

    /**
     * <b>GET /api/v1/admin/hotels/{hotelId}/rooms/{roomId}</b> — one room type.
     * <p>
     * Used by the edit screen. The room type must belong to the given hotel.
     * <p>
     * Access: HOTEL_MANAGER, owner of the hotel.<br>
     * 200 OK. 404: unknown or deleted room type, or not the caller's hotel.
     *
     * @param hotelId hotel id
     * @param roomId room type id
     * @return the room type
     */
    @GetMapping("/{roomId}")
    public RoomResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId, @PathVariable Long roomId) {
        log.debug("GET /api/v1/admin/hotels/{hotelId}/rooms/{roomId} hotelId={} roomId={}", hotelId, roomId);
        return roomService.get(currentUser.requireId(jwt), hotelId, roomId);
    }

    /**
     * <b>PUT /api/v1/admin/hotels/{hotelId}/rooms/{roomId}</b> (multipart/form-data) — edit a room type.
     * <p>
     * Replaces the editable fields (plus optional {@code removedPhotoIds}) and appends new photos. A new
     * totalCount or basePrice is pushed into all future inventory dates (price = base price x that date's
     * surge factor).
     * <p>
     * Access: HOTEL_MANAGER, owner of the hotel.<br>
     * 200 OK: the updated room type. 400: invalid fields or image. 404: not found.
     * 409: another room type already has that name, or totalCount is lower than the rooms already booked
     * on some future date.
     *
     * @param hotelId hotel id
     * @param roomId room type id
     * @param request new details (the "data" part)
     * @param photos optional new photos to add
     * @return the updated room type
     */
    @PutMapping(value = "/{roomId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RoomResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId, @PathVariable Long roomId,
                               @Valid @RequestPart("data") RoomRequest request,
                               @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
        log.debug("PUT /api/v1/admin/hotels/{hotelId}/rooms/{roomId} hotelId={} roomId={}", hotelId, roomId);
        return roomService.update(currentUser.requireId(jwt), hotelId, roomId, request, photos);
    }

    /**
     * <b>DELETE /api/v1/admin/hotels/{hotelId}/rooms/{roomId}</b> — delete (soft delete) a room type.
     * <p>
     * Marks it deleted and closes its future dates; booking history is kept.
     * <p>
     * Access: HOTEL_MANAGER, owner of the hotel.<br>
     * 204 No Content. 404: not found. 409: it still has upcoming bookings; cancel them first.
     *
     * @param hotelId hotel id
     * @param roomId room type id
     */
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{roomId}")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId, @PathVariable Long roomId) {
        log.debug("DELETE /api/v1/admin/hotels/{hotelId}/rooms/{roomId} hotelId={} roomId={}", hotelId, roomId);
        roomService.delete(currentUser.requireId(jwt), hotelId, roomId);
    }

    /**
     * <b>GET /api/v1/admin/hotels/{hotelId}/rooms/{roomId}/photos/{photoId}</b> — one room-type photo.
     * <p>
     * Streams the stored image (private, 1-hour cache), also for inactive hotels.
     * <p>
     * Access: HOTEL_MANAGER, owner of the hotel.<br>
     * 200 OK (image). 404: unknown photo, room type or hotel.
     *
     * @param hotelId hotel id
     * @param roomId room type id
     * @param photoId photo id
     * @return the image
     */
    @GetMapping("/{roomId}/photos/{photoId}")
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId,
                                        @PathVariable Long roomId, @PathVariable Long photoId) {
        log.debug("GET /api/v1/admin/hotels/{hotelId}/rooms/{roomId}/photos/{photoId} hotelId={} roomId={} photoId={}", hotelId, roomId, photoId);
        return PhotoResponses.of(roomService.getPhoto(currentUser.requireId(jwt), hotelId, roomId, photoId));
    }

    /**
     * <b>GET /api/v1/admin/hotels/{hotelId}/rooms/{roomId}/inventory?startDate=&amp;endDate=</b>
     * — day-by-day availability of a room type.
     * <p>
     * For each date from startDate to endDate (inclusive, ISO yyyy-MM-dd, at most 366 days): total rooms,
     * booked rooms, available rooms, whether the date is closed, surge factor and price per night.
     * Powers the manager's availability calendar.
     * <p>
     * Access: HOTEL_MANAGER, owner of the hotel.<br>
     * 200 OK. 400: missing/invalid dates or range too long. 404: not found.
     *
     * @param hotelId hotel id
     * @param roomId room type id
     * @param startDate first date (inclusive)
     * @param endDate last date (inclusive)
     * @return one entry per date that has inventory
     */
    @GetMapping("/{roomId}/inventory")
    public List<InventoryDayResponse> inventory(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId,
                                                @PathVariable Long roomId,
                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.debug("GET /api/v1/admin/hotels/{hotelId}/rooms/{roomId}/inventory hotelId={} roomId={} startDate={} endDate={}", hotelId, roomId, startDate, endDate);
        return inventoryService.get(currentUser.requireId(jwt), hotelId, roomId, startDate, endDate);
    }

    /**
     * <b>PATCH /api/v1/admin/hotels/{hotelId}/rooms/{roomId}/inventory</b> — change availability or pricing for a date range.
     * <p>
     * Body: {@code {"startDate", "endDate", "closed"?, "surgeFactor"?}} (endDate inclusive, start today or
     * later). {@code closed = true} stops new bookings on those dates (existing bookings are kept);
     * {@code surgeFactor} (0.50–5.00) sets the price to base price x factor. At least one of them is
     * required. Rows are locked while updating, so concurrent bookings stay consistent.
     * <p>
     * Access: HOTEL_MANAGER, owner of the hotel.<br>
     * 200 OK: {@code {"updatedDays"}}. 400: past dates, invalid range or factor, nothing to change.
     * 404: not found.
     *
     * @param hotelId hotel id
     * @param roomId room type id
     * @param request the dates and changes
     * @return how many dates were updated
     */
    @PatchMapping("/{roomId}/inventory")
    public InventoryUpdateResponse updateInventory(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId,
                                                   @PathVariable Long roomId,
                                                   @Valid @RequestBody InventoryUpdateRequest request) {
        log.debug("PATCH /api/v1/admin/hotels/{hotelId}/rooms/{roomId}/inventory hotelId={} roomId={}", hotelId, roomId);
        return inventoryService.update(currentUser.requireId(jwt), hotelId, roomId, request);
    }
}
