package com.HotelManagement.AirBnb.hotel.service;

import com.HotelManagement.AirBnb.booking.repository.BookingRepository;
import com.HotelManagement.AirBnb.booking.service.BookingService;
import com.HotelManagement.AirBnb.hotel.dto.PhotoMetadata;
import com.HotelManagement.AirBnb.hotel.dto.PhotoResponse;
import com.HotelManagement.AirBnb.hotel.dto.RoomRequest;
import com.HotelManagement.AirBnb.hotel.dto.RoomResponse;
import com.HotelManagement.AirBnb.hotel.entity.Hotel;
import com.HotelManagement.AirBnb.hotel.entity.Inventory;
import com.HotelManagement.AirBnb.hotel.entity.Room;
import com.HotelManagement.AirBnb.hotel.entity.RoomPhoto;
import com.HotelManagement.AirBnb.hotel.repository.InventoryRepository;
import com.HotelManagement.AirBnb.hotel.repository.RoomPhotoRepository;
import com.HotelManagement.AirBnb.hotel.repository.RoomRepository;
import com.HotelManagement.AirBnb.shared.exception.ConflictException;
import com.HotelManagement.AirBnb.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Room types of a hotel. A room type's totalCount is mirrored into its date-wise inventory. */
@Service
public class RoomService {

    /** Inventory is opened this many days ahead when a room type is created (see DB_Info). */
    public static final int INVENTORY_DAYS = 365;

    private final RoomRepository roomRepository;
    private final RoomPhotoRepository photoRepository;
    private final InventoryRepository inventoryRepository;
    private final BookingRepository bookingRepository;
    private final HotelService hotelService;
    private final AmenityService amenityService;
    private final ImageValidator imageValidator;

    public RoomService(RoomRepository roomRepository, RoomPhotoRepository photoRepository,
                       InventoryRepository inventoryRepository, BookingRepository bookingRepository,
                       HotelService hotelService, AmenityService amenityService, ImageValidator imageValidator) {
        this.roomRepository = roomRepository;
        this.photoRepository = photoRepository;
        this.inventoryRepository = inventoryRepository;
        this.bookingRepository = bookingRepository;
        this.hotelService = hotelService;
        this.amenityService = amenityService;
        this.imageValidator = imageValidator;
    }

    @Transactional
    public RoomResponse create(Long ownerId, Long hotelId, RoomRequest request, List<MultipartFile> photos) {
        Hotel hotel = hotelService.requireOwned(ownerId, hotelId);
        List<MultipartFile> files = imageValidator.nonNull(photos);
        imageValidator.checkPhotoLimit(0, files.size());
        String type = request.type().strip();
        if (roomRepository.existsLiveTypeInHotel(hotelId, type)) {
            throw new ConflictException("A room type named '" + type + "' already exists for this hotel");
        }

        Room room = new Room();
        room.setHotel(hotel);
        apply(room, request);
        roomRepository.save(room);
        savePhotos(room, files);

        LocalDate today = LocalDate.now();
        inventoryRepository.generate(hotelId, room.getId(), room.getTotalCount(), room.getBasePrice(), hotel.getCity(),
                today, today.plusDays(INVENTORY_DAYS));
        return toResponse(room);
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> list(Long ownerId, Long hotelId) {
        hotelService.requireOwned(ownerId, hotelId);
        List<Room> rooms = roomRepository.findLiveByHotel(hotelId);
        if (rooms.isEmpty()) {
            return List.of();
        }
        Map<Long, List<PhotoMetadata>> photos = photoRepository
                .findMetadataByRoomIds(rooms.stream().map(Room::getId).toList()).stream()
                .collect(Collectors.groupingBy(PhotoMetadata::ownerId));
        return rooms.stream().map(r -> toResponse(r, photos.getOrDefault(r.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public RoomResponse get(Long ownerId, Long hotelId, Long roomId) {
        return toResponse(requireOwned(ownerId, hotelId, roomId));
    }

    @Transactional
    public RoomResponse update(Long ownerId, Long hotelId, Long roomId, RoomRequest request, List<MultipartFile> photos) {
        hotelService.requireOwned(ownerId, hotelId);
        Room room = roomRepository.findForUpdate(roomId, hotelId).orElseThrow(() -> notFound(roomId));
        List<MultipartFile> files = imageValidator.nonNull(photos);
        String type = request.type().strip();
        if (roomRepository.existsOtherLiveTypeInHotel(hotelId, type, roomId)) {
            throw new ConflictException("A room type named '" + type + "' already exists for this hotel");
        }

        boolean photosChanged = false;
        if (request.removedPhotoIds() != null && !request.removedPhotoIds().isEmpty()) {
            photosChanged = photoRepository.deleteInRoom(roomId, request.removedPhotoIds()) > 0;
        }
        imageValidator.checkPhotoLimit(photoRepository.countInRoom(roomId), files.size());

        boolean countChanged = !room.getTotalCount().equals(request.totalCount());
        boolean priceChanged = room.getBasePrice().compareTo(request.basePrice()) != 0;
        apply(room, request);
        if (countChanged || priceChanged) {
            syncFutureInventory(room);
        }
        savePhotos(room, files);
        if (photosChanged || !files.isEmpty()) {
            room.setUpdatedAt(LocalDateTime.now());
        }
        roomRepository.flush();
        return toResponse(room);
    }

    /** Soft delete; refused while bookings hold inventory for upcoming stays. */
    @Transactional
    public void delete(Long ownerId, Long hotelId, Long roomId) {
        hotelService.requireOwned(ownerId, hotelId);
        Room room = roomRepository.findForUpdate(roomId, hotelId).orElseThrow(() -> notFound(roomId));
        LocalDate today = LocalDate.now();
        if (bookingRepository.existsUpcomingForRoom(roomId, BookingService.INVENTORY_HOLDING_STATUS_NAMES, today)) {
            throw new ConflictException("This room type has upcoming bookings. Cancel them before deleting it.");
        }
        room.setDeletedAt(LocalDateTime.now());
        inventoryRepository.closeRoomFrom(roomId, today);
    }

    @Transactional(readOnly = true)
    public RoomPhoto getPhoto(Long ownerId, Long hotelId, Long roomId, Long photoId) {
        requireOwned(ownerId, hotelId, roomId);
        return photoRepository.findInRoom(photoId, roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));
    }

    Room requireOwned(Long ownerId, Long hotelId, Long roomId) {
        hotelService.requireOwned(ownerId, hotelId);
        return roomRepository.findLiveInHotel(roomId, hotelId)
                .orElseThrow(() -> notFound(roomId));
    }

    /**
     * Pushes a new totalCount/basePrice into future inventory. Rows are locked first; the room
     * count can't drop below what is already booked on any future date.
     */
    private void syncFutureInventory(Room room) {
        LocalDate today = LocalDate.now();
        List<Inventory> rows = inventoryRepository.findRangeForUpdate(room.getId(), today, today.plusYears(5));
        for (Inventory inv : rows) {
            if (inv.getBookedCount() > room.getTotalCount()) {
                throw new ConflictException(inv.getBookedCount() + " rooms are already booked on " + inv.getDate()
                        + ". The number of rooms can't be lower than that.");
            }
        }
        for (Inventory inv : rows) {
            inv.setTotalCount(room.getTotalCount());
            inv.setPrice(price(room.getBasePrice(), inv.getSurgeFactor()));
        }
    }

    private void apply(Room room, RoomRequest request) {
        room.setType(request.type().strip());
        room.setBasePrice(request.basePrice().setScale(2, RoundingMode.HALF_UP));
        room.setTotalCount(request.totalCount());
        room.setCapacity(request.capacity());
        room.setAmenities(amenityService.normalize(request.amenities()));
    }

    private void savePhotos(Room room, List<MultipartFile> files) {
        for (MultipartFile file : files) {
            RoomPhoto photo = imageValidator.toPhoto(file, new RoomPhoto());
            photo.setRoom(room);
            photoRepository.save(photo);
        }
    }

    private RoomResponse toResponse(Room room) {
        return toResponse(room, photoRepository.findMetadataByRoomIds(List.of(room.getId())));
    }

    private RoomResponse toResponse(Room room, List<PhotoMetadata> photos) {
        Long hotelId = room.getHotel().getHotelId();
        List<PhotoResponse> photoDtos = photos.stream()
                .map(p -> new PhotoResponse(p.id(), p.fileName(), p.contentType(), p.sizeBytes(),
                        "/api/v1/admin/hotels/" + hotelId + "/rooms/" + room.getId() + "/photos/" + p.id()))
                .toList();
        return new RoomResponse(room.getId(), hotelId, room.getType(), room.getBasePrice(), room.getTotalCount(),
                room.getCapacity(), amenityService.toList(room.getAmenities()), photoDtos, room.getCreatedAt(),
                room.getUpdatedAt());
    }

    private static ResourceNotFoundException notFound(Long roomId) {
        return new ResourceNotFoundException("Room type " + roomId + " not found");
    }

    static BigDecimal price(BigDecimal basePrice, BigDecimal surge) {
        return basePrice.multiply(surge).setScale(2, RoundingMode.HALF_UP);
    }
}
