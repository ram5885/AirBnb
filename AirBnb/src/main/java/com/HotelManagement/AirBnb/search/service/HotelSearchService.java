package com.HotelManagement.AirBnb.search.service;

import com.HotelManagement.AirBnb.booking.service.BookingService;
import com.HotelManagement.AirBnb.hotel.dto.PhotoMetadata;
import com.HotelManagement.AirBnb.hotel.entity.Hotel;
import com.HotelManagement.AirBnb.hotel.entity.HotelPhoto;
import com.HotelManagement.AirBnb.hotel.entity.Inventory;
import com.HotelManagement.AirBnb.hotel.entity.Room;
import com.HotelManagement.AirBnb.hotel.entity.RoomPhoto;
import com.HotelManagement.AirBnb.hotel.repository.HotelPhotoRepository;
import com.HotelManagement.AirBnb.hotel.repository.HotelRepository;
import com.HotelManagement.AirBnb.hotel.repository.InventoryRepository;
import com.HotelManagement.AirBnb.hotel.repository.RoomPhotoRepository;
import com.HotelManagement.AirBnb.hotel.repository.RoomRepository;
import com.HotelManagement.AirBnb.hotel.service.AmenityService;
import com.HotelManagement.AirBnb.location.entity.Location;
import com.HotelManagement.AirBnb.location.repository.LocationRepository;
import com.HotelManagement.AirBnb.search.dto.AvailabilityResponse;
import com.HotelManagement.AirBnb.search.dto.HotelSearchResult;
import com.HotelManagement.AirBnb.search.dto.PublicHotelResponse;
import com.HotelManagement.AirBnb.search.dto.PublicRoomResponse;
import com.HotelManagement.AirBnb.search.dto.PublicRoomTypeSummary;
import com.HotelManagement.AirBnb.search.dto.StayResponse;
import com.HotelManagement.AirBnb.shared.dto.PageResponse;
import com.HotelManagement.AirBnb.shared.exception.BadRequestException;
import com.HotelManagement.AirBnb.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Guest-facing hotel search and details. Only active, non-deleted hotels and room types are visible,
 * and availability is read from date-wise Inventory rows (one per room type per night), whose
 * bookedCount already accounts for every inventory-holding booking. Bookings re-check availability
 * under row locks in BookingService, so these answers are informational, never a reservation.
 */
@Service
@Transactional(readOnly = true)
public class HotelSearchService {

    public static final int MAX_PAGE_SIZE = 50;
    private static final int SEARCH_PHOTOS_PER_HOTEL = 5;

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final HotelPhotoRepository hotelPhotoRepository;
    private final RoomPhotoRepository roomPhotoRepository;
    private final AmenityService amenityService;

    public HotelSearchService(HotelRepository hotelRepository, RoomRepository roomRepository,
                              InventoryRepository inventoryRepository, LocationRepository locationRepository,
                              HotelPhotoRepository hotelPhotoRepository, RoomPhotoRepository roomPhotoRepository,
                              AmenityService amenityService) {
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
        this.inventoryRepository = inventoryRepository;
        this.locationRepository = locationRepository;
        this.hotelPhotoRepository = hotelPhotoRepository;
        this.roomPhotoRepository = roomPhotoRepository;
        this.amenityService = amenityService;
    }

    /** Validated stay parameters. */
    public record Stay(LocalDate checkIn, LocalDate checkOut, int rooms) {
        long nights() {
            return ChronoUnit.DAYS.between(checkIn, checkOut);
        }

        StayResponse toResponse() {
            return new StayResponse(checkIn, checkOut, nights(), rooms);
        }
    }

    /** Applies the same rules as booking: check-in today or later, 1-30 nights, 1-10 rooms. */
    public Stay requireStay(LocalDate checkIn, LocalDate checkOut, Integer rooms) {
        if (checkIn == null || checkOut == null) {
            throw new BadRequestException("Check-in and check-out dates are required");
        }
        if (checkIn.isBefore(LocalDate.now())) {
            throw new BadRequestException("Check-in date can't be in the past");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new BadRequestException("Check-out date must be after the check-in date");
        }
        if (ChronoUnit.DAYS.between(checkIn, checkOut) > BookingService.MAX_NIGHTS) {
            throw new BadRequestException("A stay can be at most " + BookingService.MAX_NIGHTS + " nights");
        }
        if (rooms == null || rooms < 1 || rooms > BookingService.MAX_ROOMS) {
            throw new BadRequestException("Number of rooms must be between 1 and " + BookingService.MAX_ROOMS);
        }
        return new Stay(checkIn, checkOut, rooms);
    }

    /** Stay for detail pages: optional, but all-or-nothing and valid when given. */
    public Stay optionalStay(LocalDate checkIn, LocalDate checkOut, Integer rooms) {
        if (checkIn == null && checkOut == null && rooms == null) {
            return null;
        }
        return requireStay(checkIn, checkOut, rooms == null ? 1 : rooms);
    }

    public PageResponse<HotelSearchResult> search(String city, String state, Stay stay, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("page must be 0 or more and size between 1 and " + MAX_PAGE_SIZE);
        }
        Location location = resolveLocation(city, state);

        Page<Hotel> hotels = hotelRepository.searchAvailable(location.getId(), stay.checkIn(), stay.checkOut(),
                stay.rooms(), stay.nights(), PageRequest.of(page, size)); // ordered by name in the query
        List<Long> ids = hotels.getContent().stream().map(Hotel::getHotelId).toList();
        if (ids.isEmpty()) {
            return PageResponse.of(hotels, List.of());
        }

        // Two set-based queries for the whole page (no per-hotel queries, no image bytes).
        Map<Long, List<RoomAvailability>> availability = availabilityByHotel(ids, stay);
        Map<Long, List<PhotoMetadata>> photos = hotelPhotoRepository.findMetadataByHotelIds(ids).stream()
                .collect(Collectors.groupingBy(PhotoMetadata::ownerId));

        return PageResponse.of(hotels, h -> {
            List<RoomAvailability> bookable = availability.getOrDefault(h.getHotelId(), List.of()).stream()
                    .filter(RoomAvailability::bookable).toList();
            RoomAvailability cheapest = bookable.stream()
                    .min(Comparator.comparing(RoomAvailability::stayPricePerRoom)).orElse(null);
            List<String> photoUrls = photos.getOrDefault(h.getHotelId(), List.of()).stream()
                    .limit(SEARCH_PHOTOS_PER_HOTEL).map(p -> hotelPhotoUrl(h.getHotelId(), p.id())).toList();
            return new HotelSearchResult(h.getHotelId(), h.getName(), h.getLocation().getCity(), h.getLocation().getState(),
                    photoUrls, amenityService.toList(h.getAmenities()), bookable.size(),
                    cheapest == null ? null : cheapest.averageNightly(stay),
                    cheapest == null ? null : cheapest.total(stay));
        });
    }

    public PublicHotelResponse getHotel(Long hotelId, Stay stay) {
        Hotel hotel = requirePublicHotel(hotelId);
        List<Room> rooms = roomRepository.findLiveByHotel(hotelId);
        Map<Long, List<PhotoMetadata>> roomPhotos = rooms.isEmpty() ? Map.of()
                : roomPhotoRepository.findMetadataByRoomIds(rooms.stream().map(Room::getId).toList()).stream()
                .collect(Collectors.groupingBy(PhotoMetadata::ownerId));
        Map<Long, RoomAvailability> availability = stay == null ? Map.of()
                : availabilityByHotel(List.of(hotelId), stay).getOrDefault(hotelId, List.of()).stream()
                .collect(Collectors.toMap(RoomAvailability::roomId, Function.identity()));

        List<PublicRoomTypeSummary> roomTypes = rooms.stream()
                .map(r -> new PublicRoomTypeSummary(r.getId(), r.getType(), r.getCapacity(), r.getBasePrice(),
                        amenityService.toList(r.getAmenities()),
                        roomPhotos.getOrDefault(r.getId(), List.of()).stream()
                                .map(p -> roomPhotoUrl(hotelId, r.getId(), p.id())).toList(),
                        stay == null ? null : toResponse(availability.get(r.getId()), stay)))
                .toList();

        List<String> photoUrls = hotelPhotoRepository.findMetadataByHotelIds(List.of(hotelId)).stream()
                .map(p -> hotelPhotoUrl(hotelId, p.id())).toList();
        return new PublicHotelResponse(hotelId, hotel.getName(), hotel.getLocation().getCity(),
                hotel.getLocation().getState(),
                hotel.getContactInfo() == null ? null : hotel.getContactInfo().getAddress(),
                hotel.getContactInfo() == null ? null : hotel.getContactInfo().getLocation(),
                amenityService.toList(hotel.getAmenities()), photoUrls,
                stay == null ? null : stay.toResponse(), roomTypes);
    }

    public PublicRoomResponse getRoom(Long hotelId, Long roomId, Stay stay) {
        Hotel hotel = requirePublicHotel(hotelId);
        Room room = roomRepository.findLiveInHotel(roomId, hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Room type not found"));
        List<String> photoUrls = roomPhotoRepository.findMetadataByRoomIds(List.of(roomId)).stream()
                .map(p -> roomPhotoUrl(hotelId, roomId, p.id())).toList();

        AvailabilityResponse availability = null;
        List<PublicRoomResponse.Night> nights = null;
        if (stay != null) {
            Map<LocalDate, Inventory> byDate = new HashMap<>();
            inventoryRepository.findRange(roomId, stay.checkIn(), stay.checkOut()).forEach(i -> byDate.put(i.getDate(), i));
            nights = new ArrayList<>();
            for (LocalDate d = stay.checkIn(); d.isBefore(stay.checkOut()); d = d.plusDays(1)) {
                Inventory inv = byDate.get(d);
                boolean open = inv != null && !Boolean.TRUE.equals(inv.getClosed());
                int free = open ? Math.max(0, inv.getTotalCount() - inv.getBookedCount()) : 0;
                nights.add(new PublicRoomResponse.Night(d, open ? inv.getPrice() : null, free, open && free >= stay.rooms()));
            }
            availability = toResponse(availabilityByHotel(List.of(hotelId), stay).getOrDefault(hotelId, List.of()).stream()
                    .filter(a -> a.roomId().equals(roomId)).findFirst().orElse(null), stay);
        }
        return new PublicRoomResponse(roomId, hotelId, hotel.getName(), hotel.getLocation().getCity(),
                hotel.getLocation().getState(), room.getType(), room.getCapacity(), room.getBasePrice(),
                amenityService.toList(room.getAmenities()), photoUrls, stay == null ? null : stay.toResponse(),
                availability, nights);
    }

    public HotelPhoto getHotelPhoto(Long hotelId, Long photoId) {
        requirePublicHotel(hotelId);
        return hotelPhotoRepository.findInHotel(photoId, hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));
    }

    public RoomPhoto getRoomPhoto(Long hotelId, Long roomId, Long photoId) {
        requirePublicHotel(hotelId);
        roomRepository.findLiveInHotel(roomId, hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Room type not found"));
        return roomPhotoRepository.findInRoom(photoId, roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));
    }

    // ------------------------------------------------------------ helpers

    /**
     * Resolves the searched city to one Location. City names can repeat across states, so an
     * ambiguous name without a state is rejected with the candidate states listed.
     */
    Location resolveLocation(String city, String state) {
        if (city == null || city.isBlank()) {
            throw new BadRequestException("City is required");
        }
        String c = city.strip();
        if (state != null && !state.isBlank()) {
            return locationRepository.findByCityAndState(c, state.strip())
                    .orElseThrow(() -> new BadRequestException("'" + c + "' is not a known city in '" + state.strip() + "'"));
        }
        List<Location> matches = locationRepository.findByCity(c);
        if (matches.isEmpty()) {
            throw new BadRequestException("'" + c + "' is not a known city. Choose a city from the suggestions.");
        }
        if (matches.size() > 1) {
            throw new BadRequestException("'" + c + "' exists in several states ("
                    + matches.stream().map(Location::getState).collect(Collectors.joining(", "))
                    + "). Please specify the state.");
        }
        return matches.getFirst();
    }

    private Hotel requirePublicHotel(Long hotelId) {
        return hotelRepository.findPublic(hotelId).orElseThrow(() -> new ResourceNotFoundException("Hotel not found"));
    }

    private record RoomAvailability(Long hotelId, Long roomId, long openNights, BigDecimal stayPricePerRoom,
                                    int minFree, boolean bookable) {
        BigDecimal total(Stay stay) {
            return stayPricePerRoom.multiply(BigDecimal.valueOf(stay.rooms())).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal averageNightly(Stay stay) {
            return stayPricePerRoom.divide(BigDecimal.valueOf(stay.nights()), 2, RoundingMode.HALF_UP);
        }
    }

    private Map<Long, List<RoomAvailability>> availabilityByHotel(List<Long> hotelIds, Stay stay) {
        Map<Long, List<RoomAvailability>> result = new HashMap<>();
        for (Object[] row : inventoryRepository.availabilityByRoom(hotelIds, stay.checkIn(), stay.checkOut())) {
            long openNights = ((Number) row[2]).longValue();
            int minFree = ((Number) row[4]).intValue();
            RoomAvailability a = new RoomAvailability((Long) row[0], (Long) row[1], openNights, (BigDecimal) row[3],
                    minFree, openNights == stay.nights() && minFree >= stay.rooms());
            result.computeIfAbsent(a.hotelId(), k -> new ArrayList<>()).add(a);
        }
        return result;
    }

    private static AvailabilityResponse toResponse(RoomAvailability a, Stay stay) {
        if (a == null || a.openNights() != stay.nights()) {
            return new AvailabilityResponse(false, 0, null, null); // some night closed or not on sale
        }
        return new AvailabilityResponse(a.bookable(), Math.max(0, a.minFree()), a.total(stay), a.averageNightly(stay));
    }

    public static String hotelPhotoUrl(Long hotelId, Long photoId) {
        return "/api/v1/hotels/" + hotelId + "/photos/" + photoId;
    }

    static String roomPhotoUrl(Long hotelId, Long roomId, Long photoId) {
        return "/api/v1/hotels/" + hotelId + "/rooms/" + roomId + "/photos/" + photoId;
    }
}
