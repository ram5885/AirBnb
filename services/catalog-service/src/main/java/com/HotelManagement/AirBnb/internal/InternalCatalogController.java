package com.HotelManagement.AirBnb.internal;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.HotelManagement.AirBnb.hotel.dto.PhotoMetadata;
import com.HotelManagement.AirBnb.hotel.entity.Hotel;
import com.HotelManagement.AirBnb.hotel.hold.HoldRequest;
import com.HotelManagement.AirBnb.hotel.hold.HoldResponse;
import com.HotelManagement.AirBnb.hotel.hold.HoldStatus;
import com.HotelManagement.AirBnb.hotel.hold.InventoryHoldService;
import com.HotelManagement.AirBnb.hotel.rating.HotelRatingRepository;
import com.HotelManagement.AirBnb.hotel.repository.HotelPhotoRepository;
import com.HotelManagement.AirBnb.hotel.repository.HotelRepository;
import com.HotelManagement.AirBnb.hotel.repository.RoomRepository;
import com.HotelManagement.AirBnb.hotel.service.HotelService;
import com.HotelManagement.AirBnb.internal.InternalDtos.AttachRequest;
import com.HotelManagement.AirBnb.internal.InternalDtos.HotelSummary;
import com.HotelManagement.AirBnb.internal.InternalDtos.PublicHotel;
import com.HotelManagement.AirBnb.internal.InternalDtos.RatingUpdate;
import com.HotelManagement.AirBnb.internal.InternalDtos.ReacquireResponse;
import com.HotelManagement.AirBnb.internal.InternalDtos.ReleaseResponse;
import com.HotelManagement.AirBnb.internal.InternalDtos.RoomSummary;
import com.HotelManagement.AirBnb.internal.InternalDtos.SummariesRequest;
import com.HotelManagement.AirBnb.internal.InternalDtos.SummariesResponse;
import com.HotelManagement.AirBnb.search.service.HotelSearchService;
import com.HotelManagement.AirBnb.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service-to-service API of catalog-service (X-Internal-Token required, never routed by the gateway).
 * Contracts are documented in docs/SERVICE_API_REFERENCE.md.
 */
@RestController
@RequestMapping("/internal")
@Validated
public class InternalCatalogController {

    private static final Logger log = LoggerFactory.getLogger(InternalCatalogController.class);

    private final InventoryHoldService holdService;
    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final HotelPhotoRepository hotelPhotoRepository;
    private final HotelRatingRepository ratingRepository;
    private final HotelService hotelService;

    public InternalCatalogController(InventoryHoldService holdService, HotelRepository hotelRepository,
                                     RoomRepository roomRepository, HotelPhotoRepository hotelPhotoRepository,
                                     HotelRatingRepository ratingRepository, HotelService hotelService) {
        this.holdService = holdService;
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
        this.hotelPhotoRepository = hotelPhotoRepository;
        this.ratingRepository = ratingRepository;
        this.hotelService = hotelService;
    }

    // ------------------------------------------------------------ inventory holds (booking-service)

    /** 200 + hold; 400/404/409 with the same messages the monolith's booking creation used. Idempotent per key. */
    @PostMapping("/inventory/holds")
    public HoldResponse hold(@Valid @RequestBody HoldRequest request) {
        log.debug("Internal hold request {}: hotel {} room {} {}..{} x{}", request.holdKey(), request.hotelId(),
                request.roomId(), request.checkInDate(), request.checkOutDate(), request.roomsCount());
        return holdService.hold(request);
    }

    @PostMapping("/inventory/holds/{holdKey}/attach")
    public HoldResponse attach(@PathVariable UUID holdKey, @Valid @RequestBody AttachRequest request) {
        log.debug("POST /internal/inventory/holds/{holdKey}/attach holdKey={}", holdKey);
        return holdService.attach(holdKey, request.bookingId());
    }

    /** Always 200: status RELEASED, or NOT_FOUND when no hold exists for the key (nothing to give back). */
    @PostMapping("/inventory/holds/{holdKey}/release")
    public ReleaseResponse release(@PathVariable UUID holdKey) {
        log.debug("Internal release of hold {}", holdKey);
        HoldStatus status = holdService.release(holdKey);
        return new ReleaseResponse(status == null ? "NOT_FOUND" : status.name());
    }

    @PostMapping("/inventory/holds/{holdKey}/reacquire")
    public ReacquireResponse reacquire(@PathVariable UUID holdKey) {
        log.debug("Internal reacquire of hold {}", holdKey);
        return new ReacquireResponse(holdService.reacquire(holdKey));
    }

    /** Holds older than olderThanMinutes that no booking has attached (reconciliation). */
    @GetMapping("/inventory/holds/unattached")
    public List<HoldResponse> unattached(@RequestParam(defaultValue = "10") int olderThanMinutes) {
        log.debug("GET /internal/inventory/holds/unattached olderThanMinutes={}", olderThanMinutes);
        return holdService.unattached(LocalDateTime.now().minusMinutes(Math.max(olderThanMinutes, 1)));
    }

    // ------------------------------------------------------------ hotel facts

    /** Current hotel and room-type facts for booking views (unknown ids are absent). */
    @PostMapping("/hotels/summaries")
    @Transactional(readOnly = true)
    public SummariesResponse summaries(@Valid @RequestBody SummariesRequest request) {
        log.debug("POST /internal/hotels/summaries");
        List<Long> hotelIds = request.hotelIds() == null ? List.of() : request.hotelIds().stream().distinct().toList();
        List<Long> roomIds = request.roomIds() == null ? List.of() : request.roomIds().stream().distinct().toList();
        List<Hotel> hotels = hotelIds.isEmpty() ? List.of() : hotelRepository.findHotelsByIds(hotelIds);
        List<Long> visible = hotels.stream()
                .filter(h -> Boolean.TRUE.equals(h.getActive()) && h.getDeletedAt() == null)
                .map(Hotel::getHotelId).toList();
        Map<Long, String> covers = visible.isEmpty() ? Map.of()
                : hotelPhotoRepository.findMetadataByHotelIds(visible).stream()
                .collect(Collectors.toMap(PhotoMetadata::ownerId,
                        p -> HotelSearchService.hotelPhotoUrl(p.ownerId(), p.id()), (a, b) -> a));
        List<HotelSummary> hotelSummaries = hotels.stream()
                .map(h -> new HotelSummary(h.getHotelId(), h.getName(), h.getLocation().getCity(),
                        h.getLocation().getState(), h.getOwnerId(), Boolean.TRUE.equals(h.getActive()),
                        h.getDeletedAt() != null, covers.get(h.getHotelId())))
                .toList();
        List<RoomSummary> roomSummaries = roomIds.isEmpty() ? List.of()
                : roomRepository.findRoomsByIds(roomIds).stream()
                .map(r -> new RoomSummary(r.getId(), r.getHotel().getHotelId(), r.getType(), r.getCapacity(),
                        r.getDeletedAt() != null))
                .toList();
        return new SummariesResponse(hotelSummaries, roomSummaries);
    }

    /** 200 when the hotel is visible to guests (active, not deleted), else 404 "Hotel not found". */
    @GetMapping("/hotels/{hotelId}/public")
    @Transactional(readOnly = true)
    public PublicHotel publicHotel(@PathVariable Long hotelId) {
        log.debug("GET /internal/hotels/{hotelId}/public hotelId={}", hotelId);
        return hotelRepository.findPublic(hotelId).map(h -> new PublicHotel(h.getHotelId(), h.getName()))
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found"));
    }

    @GetMapping("/owners/{ownerId}/hotel-counts")
    public HotelService.HotelCounts hotelCounts(@PathVariable Long ownerId) {
        log.debug("GET /internal/owners/{ownerId}/hotel-counts ownerId={}", ownerId);
        return hotelService.hotelCounts(ownerId);
    }

    // ------------------------------------------------------------ ratings (review-service)

    /** Upserts absolute (sum, count) per hotel; idempotent. */
    @PutMapping("/hotels/ratings")
    @Transactional
    public Map<String, Integer> ratings(@RequestBody @Size(max = 10_000) List<@Valid RatingUpdate> updates) {
        log.debug("Rating push for {} hotel(s)", updates.size());
        int n = 0;
        for (RatingUpdate u : updates) {
            n += ratingRepository.upsert(u.hotelId(), u.ratingSum(), u.ratingCount());
        }
        return Map.of("updated", n);
    }
}
