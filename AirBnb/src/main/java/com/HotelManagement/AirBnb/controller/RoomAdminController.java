package com.HotelManagement.AirBnb.controller;

import com.HotelManagement.AirBnb.dto.inventory.InventoryDayResponse;
import com.HotelManagement.AirBnb.dto.inventory.InventoryUpdateRequest;
import com.HotelManagement.AirBnb.dto.inventory.InventoryUpdateResponse;
import com.HotelManagement.AirBnb.dto.room.RoomRequest;
import com.HotelManagement.AirBnb.dto.room.RoomResponse;
import com.HotelManagement.AirBnb.service.CurrentUserService;
import com.HotelManagement.AirBnb.service.InventoryService;
import com.HotelManagement.AirBnb.service.RoomService;
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

    private final RoomService roomService;
    private final InventoryService inventoryService;
    private final CurrentUserService currentUser;

    public RoomAdminController(RoomService roomService, InventoryService inventoryService, CurrentUserService currentUser) {
        this.roomService = roomService;
        this.inventoryService = inventoryService;
        this.currentUser = currentUser;
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RoomResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId,
                               @Valid @RequestPart("data") RoomRequest request,
                               @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
        return roomService.create(currentUser.require(jwt).getId(), hotelId, request, photos);
    }

    @GetMapping
    public List<RoomResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId) {
        return roomService.list(currentUser.require(jwt).getId(), hotelId);
    }

    @GetMapping("/{roomId}")
    public RoomResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId, @PathVariable Long roomId) {
        return roomService.get(currentUser.require(jwt).getId(), hotelId, roomId);
    }

    @PutMapping(value = "/{roomId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RoomResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId, @PathVariable Long roomId,
                               @Valid @RequestPart("data") RoomRequest request,
                               @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
        return roomService.update(currentUser.require(jwt).getId(), hotelId, roomId, request, photos);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{roomId}")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId, @PathVariable Long roomId) {
        roomService.delete(currentUser.require(jwt).getId(), hotelId, roomId);
    }

    @GetMapping("/{roomId}/photos/{photoId}")
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId,
                                        @PathVariable Long roomId, @PathVariable Long photoId) {
        return PhotoResponses.of(roomService.getPhoto(currentUser.require(jwt).getId(), hotelId, roomId, photoId));
    }

    // startDate..endDate inclusive, ISO dates (yyyy-MM-dd)
    @GetMapping("/{roomId}/inventory")
    public List<InventoryDayResponse> inventory(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId,
                                                @PathVariable Long roomId,
                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return inventoryService.get(currentUser.require(jwt).getId(), hotelId, roomId, startDate, endDate);
    }

    @PatchMapping("/{roomId}/inventory")
    public InventoryUpdateResponse updateInventory(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId,
                                                   @PathVariable Long roomId,
                                                   @Valid @RequestBody InventoryUpdateRequest request) {
        return inventoryService.update(currentUser.require(jwt).getId(), hotelId, roomId, request);
    }
}
