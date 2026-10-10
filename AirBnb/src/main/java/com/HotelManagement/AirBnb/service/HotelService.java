package com.HotelManagement.AirBnb.service;

import com.HotelManagement.AirBnb.dto.PageResponse;
import com.HotelManagement.AirBnb.dto.PhotoMetadata;
import com.HotelManagement.AirBnb.dto.PhotoResponse;
import com.HotelManagement.AirBnb.dto.hotel.ContactInfoResponse;
import com.HotelManagement.AirBnb.dto.hotel.DashboardResponse;
import com.HotelManagement.AirBnb.dto.hotel.HotelDetailResponse;
import com.HotelManagement.AirBnb.dto.hotel.HotelRequest;
import com.HotelManagement.AirBnb.dto.hotel.HotelSummaryResponse;
import com.HotelManagement.AirBnb.entity.Hotel;
import com.HotelManagement.AirBnb.entity.HotelContactInfo;
import com.HotelManagement.AirBnb.entity.HotelPhoto;
import com.HotelManagement.AirBnb.entity.Location;
import com.HotelManagement.AirBnb.entity.Room;
import com.HotelManagement.AirBnb.entity.User;
import com.HotelManagement.AirBnb.entity.enums.BookingStatus;
import com.HotelManagement.AirBnb.exception.BadRequestException;
import com.HotelManagement.AirBnb.exception.ConflictException;
import com.HotelManagement.AirBnb.exception.ResourceNotFoundException;
import com.HotelManagement.AirBnb.repository.BookingRepository;
import com.HotelManagement.AirBnb.repository.HotelPhotoRepository;
import com.HotelManagement.AirBnb.repository.HotelRepository;
import com.HotelManagement.AirBnb.repository.InventoryRepository;
import com.HotelManagement.AirBnb.repository.LocationRepository;
import com.HotelManagement.AirBnb.repository.RoomRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Hotel management for hotel managers. Every lookup is scoped to the authenticated owner. */
@Service
public class HotelService {

    private final HotelRepository hotelRepository;
    private final HotelPhotoRepository photoRepository;
    private final RoomRepository roomRepository;
    private final InventoryRepository inventoryRepository;
    private final BookingRepository bookingRepository;
    private final LocationRepository locationRepository;
    private final AmenityService amenityService;
    private final ImageValidator imageValidator;

    public HotelService(HotelRepository hotelRepository, HotelPhotoRepository photoRepository,
                        RoomRepository roomRepository, InventoryRepository inventoryRepository,
                        BookingRepository bookingRepository, LocationRepository locationRepository,
                        AmenityService amenityService, ImageValidator imageValidator) {
        this.hotelRepository = hotelRepository;
        this.photoRepository = photoRepository;
        this.roomRepository = roomRepository;
        this.inventoryRepository = inventoryRepository;
        this.bookingRepository = bookingRepository;
        this.locationRepository = locationRepository;
        this.amenityService = amenityService;
        this.imageValidator = imageValidator;
    }

    @Transactional
    public HotelDetailResponse create(User owner, HotelRequest request, List<MultipartFile> photos) {
        List<MultipartFile> files = imageValidator.nonNull(photos);
        imageValidator.checkPhotoLimit(0, files.size());

        Hotel hotel = new Hotel();
        hotel.setOwner(owner);
        apply(hotel, request);
        hotelRepository.save(hotel);
        savePhotos(hotel, files);
        return toDetail(hotel);
    }

    @Transactional(readOnly = true)
    public PageResponse<HotelSummaryResponse> list(Long ownerId, Boolean active, int page, int size) {
        // Ordered newest first by the query itself, so the page request carries no sort.
        Page<Hotel> hotels = hotelRepository.findOwnerPage(ownerId, active, PageRequest.of(page, size));
        List<Long> ids = hotels.getContent().stream().map(Hotel::getHotelId).toList();
        Map<Long, List<PhotoMetadata>> photosByHotel = ids.isEmpty() ? Map.of()
                : photoRepository.findMetadataByHotelIds(ids).stream()
                .collect(Collectors.groupingBy(PhotoMetadata::ownerId));
        Map<Long, Long> roomCounts = ids.isEmpty() ? Map.of()
                : roomRepository.countLiveByHotelIds(ids).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));

        return PageResponse.of(hotels, h -> {
            List<PhotoMetadata> hotelPhotos = photosByHotel.getOrDefault(h.getHotelId(), List.of());
            String cover = hotelPhotos.isEmpty() ? null : photoUrl(h.getHotelId(), hotelPhotos.getFirst().id());
            return new HotelSummaryResponse(h.getHotelId(), h.getName(), h.getLocation().getCity(),
                    h.getLocation().getState(), h.getActive(), cover, hotelPhotos.size(),
                    roomCounts.getOrDefault(h.getHotelId(), 0L), h.getCreatedAt(), h.getUpdatedAt());
        });
    }

    @Transactional(readOnly = true)
    public HotelDetailResponse get(Long ownerId, Long hotelId) {
        return toDetail(requireOwned(ownerId, hotelId));
    }

    @Transactional
    public HotelDetailResponse update(Long ownerId, Long hotelId, HotelRequest request, List<MultipartFile> photos) {
        // Row lock serializes with bookings, which hold a shared lock on the hotel while reserving.
        Hotel hotel = hotelRepository.findOwnedForUpdate(hotelId, ownerId)
                .orElseThrow(() -> notFound(hotelId));
        List<MultipartFile> files = imageValidator.nonNull(photos);

        boolean photosChanged = false;
        if (request.removedPhotoIds() != null && !request.removedPhotoIds().isEmpty()) {
            photosChanged = photoRepository.deleteInHotel(hotelId, request.removedPhotoIds()) > 0;
        }
        imageValidator.checkPhotoLimit(photoRepository.countInHotel(hotelId), files.size());

        String previousCity = hotel.getCity();
        apply(hotel, request);
        if (!hotel.getCity().equals(previousCity)) {
            inventoryRepository.updateCityForHotel(hotelId, hotel.getCity());
        }
        savePhotos(hotel, files);
        if (photosChanged || !files.isEmpty()) {
            hotel.setUpdatedAt(LocalDateTime.now()); // photo-only edits still count as a modification
        }
        hotelRepository.flush();
        return toDetail(hotel);
    }

    @Transactional
    public HotelDetailResponse setActive(Long ownerId, Long hotelId, boolean active) {
        Hotel hotel = hotelRepository.findOwnedForUpdate(hotelId, ownerId)
                .orElseThrow(() -> notFound(hotelId));
        hotel.setActive(active);
        hotelRepository.flush();
        return toDetail(hotel);
    }

    /**
     * Soft delete. Refused while bookings still hold inventory for upcoming stays; those must be
     * cancelled first. Past/cancelled bookings keep pointing at the (hidden) hotel and room types.
     */
    @Transactional
    public void delete(Long ownerId, Long hotelId) {
        Hotel hotel = hotelRepository.findOwnedForUpdate(hotelId, ownerId)
                .orElseThrow(() -> notFound(hotelId));
        LocalDate today = LocalDate.now();
        if (bookingRepository.existsUpcomingForHotel(hotelId, BookingService.INVENTORY_HOLDING_STATUS_NAMES, today)) {
            throw new ConflictException("This hotel has upcoming bookings. Cancel them or deactivate the hotel instead.");
        }
        LocalDateTime now = LocalDateTime.now();
        hotel.setActive(false);
        hotel.setDeletedAt(now);
        for (Room room : roomRepository.findLiveByHotel(hotelId)) {
            room.setDeletedAt(now);
        }
        inventoryRepository.closeHotelFrom(hotelId, today);
    }

    @Transactional(readOnly = true)
    public HotelPhoto getPhoto(Long ownerId, Long hotelId, Long photoId) {
        requireOwned(ownerId, hotelId);
        return photoRepository.findInHotel(photoId, hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(Long ownerId) {
        Map<BookingStatus, Long> counts = new EnumMap<>(BookingStatus.class);
        for (BookingStatus s : BookingStatus.values()) {
            counts.put(s, 0L);
        }
        for (Object[] row : bookingRepository.countByStatusForOwner(ownerId)) {
            counts.put(BookingStatus.valueOf((String) row[0]), ((Number) row[1]).longValue());
        }
        Map<String, Long> byName = new LinkedHashMap<>();
        counts.forEach((k, v) -> byName.put(k.name(), v));
        return new DashboardResponse(hotelRepository.countOwnedLive(ownerId),
                hotelRepository.countOwnedActive(ownerId), byName);
    }

    /** Owned, not-deleted hotel or 404 (other managers' hotels are indistinguishable from missing ones). */
    Hotel requireOwned(Long ownerId, Long hotelId) {
        return hotelRepository.findOwned(hotelId, ownerId)
                .orElseThrow(() -> notFound(hotelId));
    }

    private void apply(Hotel hotel, HotelRequest request) {
        Location location = locationRepository
                .findByCityAndState(request.city().strip(), request.state().strip())
                .orElseThrow(() -> new BadRequestException(
                        "'" + request.city().strip() + "' is not a valid city in '" + request.state().strip()
                                + "'. Select a city from the suggestions."));

        hotel.setName(request.name().strip());
        hotel.setLocation(location);
        hotel.setCity(location.getCity());
        hotel.setAmenities(amenityService.normalize(request.amenities()));
        hotel.setActive(request.active());

        HotelContactInfo contact = new HotelContactInfo();
        contact.setContactPerson(request.contactPerson().strip());
        contact.setAddress(request.address().strip());
        contact.setLocation(request.landmark() == null || request.landmark().isBlank() ? null : request.landmark().strip());
        contact.setPhoneNumber(request.phoneNumber().replaceAll("[\\s-]", ""));
        contact.setEmail(request.email().strip().toLowerCase());
        hotel.setContactInfo(contact);
    }

    private void savePhotos(Hotel hotel, List<MultipartFile> files) {
        for (MultipartFile file : files) {
            HotelPhoto photo = imageValidator.toPhoto(file, new HotelPhoto());
            photo.setHotel(hotel);
            photoRepository.save(photo);
        }
    }

    private HotelDetailResponse toDetail(Hotel hotel) {
        Long id = hotel.getHotelId();
        List<PhotoResponse> photos = photoRepository.findMetadataByHotelIds(List.of(id)).stream()
                .map(p -> new PhotoResponse(p.id(), p.fileName(), p.contentType(), p.sizeBytes(), photoUrl(id, p.id())))
                .toList();
        long roomTypes = roomRepository.countLiveByHotelIds(List.of(id)).stream()
                .mapToLong(r -> (Long) r[1]).sum();
        HotelContactInfo c = hotel.getContactInfo();
        ContactInfoResponse contact = c == null ? null
                : new ContactInfoResponse(c.getContactPerson(), c.getAddress(), c.getLocation(), c.getPhoneNumber(), c.getEmail());
        return new HotelDetailResponse(id, hotel.getName(), hotel.getLocation().getId(), hotel.getLocation().getCity(),
                hotel.getLocation().getState(), contact, amenityService.toList(hotel.getAmenities()), hotel.getActive(),
                photos, roomTypes, hotel.getCreatedAt(), hotel.getUpdatedAt());
    }

    static String photoUrl(Long hotelId, Long photoId) {
        return "/api/v1/admin/hotels/" + hotelId + "/photos/" + photoId;
    }

    static ResourceNotFoundException notFound(Long hotelId) {
        return new ResourceNotFoundException("Hotel " + hotelId + " not found");
    }
}
