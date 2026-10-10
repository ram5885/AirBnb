package com.HotelManagement.AirBnb.controller;

import com.HotelManagement.AirBnb.dto.PageResponse;
import com.HotelManagement.AirBnb.dto.search.HotelSearchResult;
import com.HotelManagement.AirBnb.dto.search.PublicHotelResponse;
import com.HotelManagement.AirBnb.dto.search.PublicRoomResponse;
import com.HotelManagement.AirBnb.service.HotelSearchService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Public, read-only hotel search for guests (no login required; see SecurityConfig).
 * Dates are ISO yyyy-MM-dd; check-in is inclusive and check-out exclusive.
 * Parameters are validated in HotelSearchService so errors carry specific messages.
 */
@RestController
@RequestMapping("/api/v1/hotels")
public class HotelSearchController {

    private final HotelSearchService searchService;

    public HotelSearchController(HotelSearchService searchService) {
        this.searchService = searchService;
    }

    // GET /api/v1/hotels/search?city=Jaipur&state=Rajasthan&checkinDate=2026-11-01&checkoutDate=2026-11-03&numberOfRooms=1
    @GetMapping("/search")
    public PageResponse<HotelSearchResult> search(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkinDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkoutDate,
            @RequestParam(required = false) Integer numberOfRooms,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return searchService.search(city, state, searchService.requireStay(checkinDate, checkoutDate, numberOfRooms),
                page, size);
    }

    // Stay parameters are optional here; when given, room types include availability for that stay.
    @GetMapping("/{hotelId}")
    public PublicHotelResponse hotel(
            @PathVariable Long hotelId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkinDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkoutDate,
            @RequestParam(required = false) Integer numberOfRooms) {
        return searchService.getHotel(hotelId, searchService.optionalStay(checkinDate, checkoutDate, numberOfRooms));
    }

    @GetMapping("/{hotelId}/rooms/{roomId}")
    public PublicRoomResponse room(
            @PathVariable Long hotelId,
            @PathVariable Long roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkinDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkoutDate,
            @RequestParam(required = false) Integer numberOfRooms) {
        return searchService.getRoom(hotelId, roomId, searchService.optionalStay(checkinDate, checkoutDate, numberOfRooms));
    }

    @GetMapping("/{hotelId}/photos/{photoId}")
    public ResponseEntity<byte[]> hotelPhoto(@PathVariable Long hotelId, @PathVariable Long photoId) {
        return PhotoResponses.of(searchService.getHotelPhoto(hotelId, photoId));
    }

    @GetMapping("/{hotelId}/rooms/{roomId}/photos/{photoId}")
    public ResponseEntity<byte[]> roomPhoto(@PathVariable Long hotelId, @PathVariable Long roomId,
                                            @PathVariable Long photoId) {
        return PhotoResponses.of(searchService.getRoomPhoto(hotelId, roomId, photoId));
    }
}
