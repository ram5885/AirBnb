package com.HotelManagement.AirBnb.search.controller;

import com.HotelManagement.AirBnb.search.dto.HotelSearchResult;
import com.HotelManagement.AirBnb.search.dto.PublicHotelResponse;
import com.HotelManagement.AirBnb.search.dto.PublicRoomResponse;
import com.HotelManagement.AirBnb.search.service.HotelSearchService;
import com.HotelManagement.AirBnb.shared.dto.PageResponse;
import com.HotelManagement.AirBnb.shared.web.PhotoResponses;
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

    /**
     * <b>GET /api/v1/hotels/search</b> — find hotels with rooms free for a stay.
     * <p>
     * Query: {@code city} (required), {@code state} (required only when the city name exists in several
     * states), {@code checkinDate} and {@code checkoutDate} (ISO dates, check-in today or later, 1–30
     * nights), {@code numberOfRooms} (1–10), {@code page} (default 0), {@code size} (1–50, default 10).
     * <p>
     * Returns active hotels in that location that have at least one room type with numberOfRooms free on
     * every night from check-in up to (not including) check-out, ordered by name. Each result has the
     * hotel's public summary (photos as URLs, amenities) plus the cheapest qualifying price per night and
     * for the whole stay. Availability comes from the inventory, which already accounts for reserved,
     * accepted and confirmed bookings; it is re-checked when booking.
     * <p>
     * Access: public.<br>
     * 200 OK: a page of results (may be empty). 400: missing/unknown/ambiguous city, invalid dates, rooms
     * or paging.
     *
     * @return one page of available hotels
     */
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

    /**
     * <b>GET /api/v1/hotels/{hotelId}[?checkinDate=&amp;checkoutDate=&amp;numberOfRooms=]</b> — public hotel page.
     * <p>
     * Name, city/state, address, landmark, amenities, photo URLs and its room types. When the optional
     * stay parameters are given, each room type also gets its availability for that stay (free rooms,
     * total and average nightly price). Private contact details (phone, email, contact person) are never
     * included.
     * <p>
     * Access: public.<br>
     * 200 OK. 400: invalid stay parameters. 404: hotel doesn't exist, is inactive or deleted.
     *
     * @param hotelId hotel id
     * @return the public hotel details
     */
    @GetMapping("/{hotelId}")
    public PublicHotelResponse hotel(
            @PathVariable Long hotelId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkinDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkoutDate,
            @RequestParam(required = false) Integer numberOfRooms) {
        return searchService.getHotel(hotelId, searchService.optionalStay(checkinDate, checkoutDate, numberOfRooms));
    }

    /**
     * <b>GET /api/v1/hotels/{hotelId}/rooms/{roomId}[?checkinDate=&amp;checkoutDate=&amp;numberOfRooms=]</b>
     * — public room-type page.
     * <p>
     * Room type name, capacity, base price, amenities and photos. With the stay parameters it adds the
     * availability summary and a night-by-night list (price per room, rooms free, bookable or not), used
     * before reserving.
     * <p>
     * Access: public.<br>
     * 200 OK. 400: invalid stay parameters. 404: hotel not public, or the room type doesn't belong to it.
     *
     * @param hotelId hotel id
     * @param roomId room type id
     * @return the public room type details
     */
    @GetMapping("/{hotelId}/rooms/{roomId}")
    public PublicRoomResponse room(
            @PathVariable Long hotelId,
            @PathVariable Long roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkinDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkoutDate,
            @RequestParam(required = false) Integer numberOfRooms) {
        return searchService.getRoom(hotelId, roomId, searchService.optionalStay(checkinDate, checkoutDate, numberOfRooms));
    }

    /**
     * <b>GET /api/v1/hotels/{hotelId}/photos/{photoId}</b> — public hotel photo.
     * <p>
     * Image bytes for guest pages (plain {@code <img src>} works, no token needed). Only for active,
     * non-deleted hotels.
     * <p>
     * Access: public. 200 OK (image). 404: photo not found or hotel not public.
     *
     * @param hotelId hotel id
     * @param photoId photo id
     * @return the image
     */
    @GetMapping("/{hotelId}/photos/{photoId}")
    public ResponseEntity<byte[]> hotelPhoto(@PathVariable Long hotelId, @PathVariable Long photoId) {
        return PhotoResponses.of(searchService.getHotelPhoto(hotelId, photoId));
    }

    /**
     * <b>GET /api/v1/hotels/{hotelId}/rooms/{roomId}/photos/{photoId}</b> — public room-type photo.
     * <p>
     * Image bytes for guest pages; the room type must belong to an active, non-deleted hotel.
     * <p>
     * Access: public. 200 OK (image). 404: not found or not public.
     *
     * @param hotelId hotel id
     * @param roomId room type id
     * @param photoId photo id
     * @return the image
     */
    @GetMapping("/{hotelId}/rooms/{roomId}/photos/{photoId}")
    public ResponseEntity<byte[]> roomPhoto(@PathVariable Long hotelId, @PathVariable Long roomId,
                                            @PathVariable Long photoId) {
        return PhotoResponses.of(searchService.getRoomPhoto(hotelId, roomId, photoId));
    }
}
