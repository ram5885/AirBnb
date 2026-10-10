package com.HotelManagement.AirBnb.hotel.controller;

import com.HotelManagement.AirBnb.auth.service.CurrentUserService;
import com.HotelManagement.AirBnb.hotel.dto.DashboardResponse;
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

    private final HotelService hotelService;
    private final CurrentUserService currentUser;

    public HotelAdminController(HotelService hotelService, CurrentUserService currentUser) {
        this.hotelService = hotelService;
        this.currentUser = currentUser;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@AuthenticationPrincipal Jwt jwt) {
        return hotelService.dashboard(currentUser.require(jwt).getId());
    }

    // multipart/form-data: "data" = HotelRequest JSON, "photos" = 0..n image files
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(value = "/hotels", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public HotelDetailResponse create(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestPart("data") HotelRequest request,
                                      @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
        return hotelService.create(currentUser.require(jwt), request, photos);
    }

    @GetMapping("/hotels")
    public PageResponse<HotelSummaryResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestParam(required = false) Boolean active,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "12") int size) {
        return hotelService.list(currentUser.require(jwt).getId(), active, Math.max(page, 0), PhotoResponses.pageSize(size));
    }

    @GetMapping("/hotels/{hotelId}")
    public HotelDetailResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId) {
        return hotelService.get(currentUser.require(jwt).getId(), hotelId);
    }

    @PutMapping(value = "/hotels/{hotelId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public HotelDetailResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId,
                                      @Valid @RequestPart("data") HotelRequest request,
                                      @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
        return hotelService.update(currentUser.require(jwt).getId(), hotelId, request, photos);
    }

    @PatchMapping("/hotels/{hotelId}/status")
    public HotelDetailResponse setStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId,
                                         @Valid @RequestBody HotelStatusRequest request) {
        return hotelService.setActive(currentUser.require(jwt).getId(), hotelId, request.active());
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/hotels/{hotelId}")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId) {
        hotelService.delete(currentUser.require(jwt).getId(), hotelId);
    }

    @GetMapping("/hotels/{hotelId}/photos/{photoId}")
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hotelId,
                                        @PathVariable Long photoId) {
        return PhotoResponses.of(hotelService.getPhoto(currentUser.require(jwt).getId(), hotelId, photoId));
    }
}
