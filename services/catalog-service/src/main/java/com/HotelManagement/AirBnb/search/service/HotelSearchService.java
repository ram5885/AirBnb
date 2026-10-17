package com.HotelManagement.AirBnb.search.service;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
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
import com.HotelManagement.AirBnb.hotel.rating.HotelRatingRepository;
import com.HotelManagement.AirBnb.search.dto.AvailabilityResponse;
import com.HotelManagement.AirBnb.search.dto.HotelSearchResult;
import com.HotelManagement.AirBnb.search.dto.HotelSectionResponse;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Guest-facing hotel search and details. Only active, non-deleted hotels and room types are visible,
 * and availability is read from date-wise Inventory rows (one per room type per night), whose
 * bookedCount already accounts for every inventory-holding booking. Bookings re-check availability
 * under row locks (inventory holds, see InventoryHoldService), so these answers are informational, never a reservation.
 */
@Service
@Transactional(readOnly = true)
public class HotelSearchService {

    private static final Logger log = LoggerFactory.getLogger(HotelSearchService.class);

    public static final int MAX_PAGE_SIZE = 50;
    public static final int MAX_SECTIONS = 50;
    public static final int MAX_SECTION_HOTELS = 20;
    private static final int SEARCH_PHOTOS_PER_HOTEL = 5;

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final HotelPhotoRepository hotelPhotoRepository;
    private final RoomPhotoRepository roomPhotoRepository;
    private final AmenityService amenityService;
    private final HotelRatingRepository ratingRepository;

    public HotelSearchService(HotelRepository hotelRepository, RoomRepository roomRepository,
                              InventoryRepository inventoryRepository, LocationRepository locationRepository,
                              HotelPhotoRepository hotelPhotoRepository, RoomPhotoRepository roomPhotoRepository,
                              AmenityService amenityService, HotelRatingRepository ratingRepository) {
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
        this.inventoryRepository = inventoryRepository;
        this.locationRepository = locationRepository;
        this.hotelPhotoRepository = hotelPhotoRepository;
        this.roomPhotoRepository = roomPhotoRepository;
        this.amenityService = amenityService;
        this.ratingRepository = ratingRepository;
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
        if (ChronoUnit.DAYS.between(checkIn, checkOut) > StayRules.MAX_NIGHTS) {
            throw new BadRequestException("A stay can be at most " + StayRules.MAX_NIGHTS + " nights");
        }
        if (rooms == null || rooms < 1 || rooms > StayRules.MAX_ROOMS) {
            throw new BadRequestException("Number of rooms must be between 1 and " + StayRules.MAX_ROOMS);
        }
        return new Stay(checkIn, checkOut, rooms);
    }

    /**
     * Optional search filters; null / empty means "not filtered". amenities are validated catalog codes
     * that must all be offered by one bookable room type or its hotel; minPrice/maxPrice bound that room
     * type's average nightly price per room for the stay (inclusive); minRating is the lowest accepted
     * average review rating (one decimal, as shown), and excludes hotels without reviews.
     */
    public record Filters(List<String> amenities, BigDecimal minPrice, BigDecimal maxPrice, BigDecimal minRating) {
        public static final Filters NONE = new Filters(List.of(), null, null, null);

        String amenitiesParam() {
            return String.join(",", amenities);
        }

        /** Whether a room type (with its and its hotel's amenities) matches the price and amenity filters. */
        boolean matches(BigDecimal stayPricePerRoom, long nights, String[] roomAmenities, String[] hotelAmenities) {
            BigDecimal n = BigDecimal.valueOf(nights);
            if (minPrice != null && stayPricePerRoom.compareTo(minPrice.multiply(n)) < 0) return false;
            if (maxPrice != null && stayPricePerRoom.compareTo(maxPrice.multiply(n)) > 0) return false;
            if (amenities.isEmpty()) return true;
            Set<String> offered = new HashSet<>();
            if (roomAmenities != null) offered.addAll(List.of(roomAmenities));
            if (hotelAmenities != null) offered.addAll(List.of(hotelAmenities));
            return offered.containsAll(amenities);
        }
    }

    public static final BigDecimal MAX_PRICE = new BigDecimal("1000000");

    /** Validates the optional filters; unknown amenity codes, negative or inverted prices and ratings outside 1-5 are rejected. */
    public Filters filters(List<String> amenities, BigDecimal minPrice, BigDecimal maxPrice, BigDecimal minRating) {
        List<String> codes = amenities == null ? List.of()
                : amenities.stream().map(String::strip).filter(a -> !a.isEmpty()).toList();
        codes = List.of(amenityService.normalize(codes)); // 400 for unknown codes; de-duplicated, catalog order
        for (BigDecimal price : new BigDecimal[]{minPrice, maxPrice}) {
            if (price != null && (price.signum() < 0 || price.compareTo(MAX_PRICE) > 0)) {
                throw new BadRequestException("Prices must be between 0 and " + MAX_PRICE.toPlainString());
            }
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("Minimum price can't be more than the maximum price");
        }
        if (minRating != null && (minRating.compareTo(BigDecimal.ONE) < 0 || minRating.compareTo(BigDecimal.valueOf(5)) > 0)) {
            throw new BadRequestException("Minimum rating must be between 1 and 5");
        }
        return new Filters(codes, minPrice, maxPrice, minRating);
    }

    /** Stay for detail pages: optional, but all-or-nothing and valid when given. */
    public Stay optionalStay(LocalDate checkIn, LocalDate checkOut, Integer rooms) {
        if (checkIn == null && checkOut == null && rooms == null) {
            return null;
        }
        return requireStay(checkIn, checkOut, rooms == null ? 1 : rooms);
    }

    public PageResponse<HotelSearchResult> search(String city, String state, Stay stay, int page, int size) {
        return search(city, state, stay, Filters.NONE, page, size);
    }

    /**
     * Hotels at the location that can take the stay and match every active filter (AND between filters, all
     * selected amenities required), filtered and paged in the database.
     */
    public PageResponse<HotelSearchResult> search(String city, String state, Stay stay, Filters filters, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("page must be 0 or more and size between 1 and " + MAX_PAGE_SIZE);
        }
        Location location = resolveLocation(city, state);

        Page<Hotel> hotels = hotelRepository.searchAvailable(location.getId(), stay.checkIn(), stay.checkOut(),
                stay.rooms(), stay.nights(), filters.amenitiesParam(), filters.minPrice(), filters.maxPrice(),
                filters.minRating(), PageRequest.of(page, size)); // ordered by name in the query
        log.debug("Search {} ({}) {}..{} x{} filters={} -> {} hotel(s)", location.getCity(), location.getState(),
                stay.checkIn(), stay.checkOut(), stay.rooms(), filters, hotels.getTotalElements());
        return PageResponse.of(hotels, toResults(hotels.getContent(), stay, filters));
    }

    /**
     * Home page "Stays in {city}" rows for a stay: cities with the most hotels that can take it first (up to
     * maxCities), each with its first perCity such hotels by name. Uses the same availability rule and result
     * shape as search, in three set-based queries whatever the number of cities.
     */
    public List<HotelSectionResponse> getSections(Stay stay, int maxCities, int perCity) {
        if (maxCities < 1 || maxCities > MAX_SECTIONS || perCity < 1 || perCity > MAX_SECTION_HOTELS) {
            throw new BadRequestException("maxCities must be between 1 and " + MAX_SECTIONS
                    + " and perCity between 1 and " + MAX_SECTION_HOTELS);
        }
        record Row(long hotelId, long locationId, long availableInCity) {
        }
        List<Row> rows = hotelRepository.findSectionHotelIds(stay.checkIn(), stay.checkOut(), stay.rooms(),
                        stay.nights(), Filters.NONE.amenitiesParam(), null, null, perCity).stream()
                .map(r -> new Row(((Number) r[0]).longValue(), ((Number) r[1]).longValue(), ((Number) r[2]).longValue()))
                .toList();
        log.debug("Sections for {}..{} x{}: {} candidate hotel(s)", stay.checkIn(), stay.checkOut(), stay.rooms(), rows.size());
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, Hotel> hotels = hotelRepository.findPublicByIds(rows.stream().map(Row::hotelId).toList()).stream()
                .collect(Collectors.toMap(Hotel::getHotelId, Function.identity()));
        Map<Long, List<Hotel>> byCity = new HashMap<>();
        Map<Long, Long> availableInCity = new HashMap<>();
        for (Row r : rows) {
            Hotel h = hotels.get(r.hotelId());
            if (h != null) {
                byCity.computeIfAbsent(r.locationId(), k -> new ArrayList<>()).add(h);
                availableInCity.put(r.locationId(), r.availableInCity());
            }
        }
        Comparator<List<Hotel>> mostAvailableFirst = Comparator
                .comparing((List<Hotel> l) -> availableInCity.get(l.getFirst().getLocation().getId())).reversed()
                .thenComparing(l -> l.getFirst().getLocation().getCity());
        List<List<Hotel>> sections = byCity.values().stream()
                .map(l -> l.stream().sorted(Comparator.comparing(Hotel::getName).thenComparing(Hotel::getHotelId)).toList())
                .sorted(mostAvailableFirst)
                .limit(maxCities)
                .toList();

        Map<Long, HotelSearchResult> results = toResults(sections.stream().flatMap(List::stream).toList(), stay, Filters.NONE).stream()
                .collect(Collectors.toMap(HotelSearchResult::id, Function.identity()));
        return sections.stream().map(l -> {
            Location loc = l.getFirst().getLocation();
            return new HotelSectionResponse(loc.getId(), loc.getCity(), loc.getState(), availableInCity.get(loc.getId()),
                    l.stream().map(h -> results.get(h.getHotelId())).toList());
        }).toList();
    }

    /**
     * Search-result cards for hotels (same order): the cheapest room type that can take the stay and matches
     * the filters, up to five photo URLs and the review rating. A fixed number of set-based queries for the
     * whole list (no per-hotel queries, no image bytes).
     */
    private List<HotelSearchResult> toResults(List<Hotel> hotels, Stay stay, Filters filters) {
        List<Long> ids = hotels.stream().map(Hotel::getHotelId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, List<RoomAvailability>> availability = availabilityByHotel(ids, stay);
        Map<Long, List<PhotoMetadata>> photos = hotelPhotoRepository.findMetadataByHotelIds(ids).stream()
                .collect(Collectors.groupingBy(PhotoMetadata::ownerId));
        Map<Long, String[]> roomAmenities = filters.amenities().isEmpty() ? Map.of()
                : roomRepository.findLiveByHotelIds(ids).stream()
                .collect(Collectors.toMap(Room::getId, r -> r.getAmenities() == null ? new String[0] : r.getAmenities()));
        Map<Long, Object[]> ratings = ratingRepository.ratingsByHotelIds(ids).stream()
                .collect(Collectors.toMap(r -> ((Number) r[0]).longValue(), Function.identity()));

        return hotels.stream().map(h -> {
            List<RoomAvailability> bookable = availability.getOrDefault(h.getHotelId(), List.of()).stream()
                    .filter(RoomAvailability::bookable)
                    .filter(a -> filters.matches(a.stayPricePerRoom(), stay.nights(), roomAmenities.get(a.roomId()), h.getAmenities()))
                    .toList();
            RoomAvailability cheapest = bookable.stream()
                    .min(Comparator.comparing(RoomAvailability::stayPricePerRoom)).orElse(null);
            List<String> photoUrls = photos.getOrDefault(h.getHotelId(), List.of()).stream()
                    .limit(SEARCH_PHOTOS_PER_HOTEL).map(p -> hotelPhotoUrl(h.getHotelId(), p.id())).toList();
            Object[] rating = ratings.get(h.getHotelId());
            return new HotelSearchResult(h.getHotelId(), h.getName(), h.getLocation().getCity(), h.getLocation().getState(),
                    photoUrls, amenityService.toList(h.getAmenities()), bookable.size(),
                    cheapest == null ? null : cheapest.averageNightly(stay),
                    cheapest == null ? null : cheapest.total(stay),
                    rating == null ? null : (BigDecimal) rating[1],
                    rating == null ? 0 : ((Number) rating[2]).longValue());
        }).toList();
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
