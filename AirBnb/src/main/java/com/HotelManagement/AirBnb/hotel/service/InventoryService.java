package com.HotelManagement.AirBnb.hotel.service;

import com.HotelManagement.AirBnb.hotel.dto.InventoryDayResponse;
import com.HotelManagement.AirBnb.hotel.dto.InventoryUpdateRequest;
import com.HotelManagement.AirBnb.hotel.dto.InventoryUpdateResponse;
import com.HotelManagement.AirBnb.hotel.entity.Inventory;
import com.HotelManagement.AirBnb.hotel.entity.Room;
import com.HotelManagement.AirBnb.hotel.repository.InventoryRepository;
import com.HotelManagement.AirBnb.shared.exception.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Date-wise availability of a room type (one Inventory row per room type per date). */
@Service
public class InventoryService {

    private static final int MAX_RANGE_DAYS = 366;

    private final InventoryRepository inventoryRepository;
    private final RoomService roomService;

    public InventoryService(InventoryRepository inventoryRepository, RoomService roomService) {
        this.inventoryRepository = inventoryRepository;
        this.roomService = roomService;
    }

    /** Inventory for startDate..endDate inclusive. */
    @Transactional(readOnly = true)
    public List<InventoryDayResponse> get(Long ownerId, Long hotelId, Long roomId, LocalDate startDate, LocalDate endDate) {
        roomService.requireOwned(ownerId, hotelId, roomId);
        validateRange(startDate, endDate);
        return inventoryRepository.findRange(roomId, startDate, endDate.plusDays(1)).stream()
                .map(i -> new InventoryDayResponse(i.getDate(), i.getTotalCount(), i.getBookedCount(),
                        Math.max(0, i.getTotalCount() - i.getBookedCount()), i.getClosed(), i.getSurgeFactor(),
                        i.getPrice()))
                .toList();
    }

    /** Opens/closes dates and/or sets the surge factor for startDate..endDate inclusive. */
    @Transactional
    public InventoryUpdateResponse update(Long ownerId, Long hotelId, Long roomId, InventoryUpdateRequest request) {
        Room room = roomService.requireOwned(ownerId, hotelId, roomId);
        validateRange(request.startDate(), request.endDate());
        if (request.startDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Past dates can't be changed");
        }
        if (request.closed() == null && request.surgeFactor() == null) {
            throw new BadRequestException("Choose whether to open/close the dates or set a surge factor");
        }
        List<Inventory> rows = inventoryRepository.findRangeForUpdate(roomId, request.startDate(), request.endDate().plusDays(1));
        for (Inventory inv : rows) {
            if (request.closed() != null) {
                inv.setClosed(request.closed());
            }
            if (request.surgeFactor() != null) {
                inv.setSurgeFactor(request.surgeFactor());
                inv.setPrice(RoomService.price(room.getBasePrice(), request.surgeFactor()));
            }
        }
        return new InventoryUpdateResponse(rows.size());
    }

    private static void validateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new BadRequestException("Start and end dates are required");
        }
        if (end.isBefore(start)) {
            throw new BadRequestException("End date must be on or after the start date");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_RANGE_DAYS) {
            throw new BadRequestException("Choose a range of at most " + MAX_RANGE_DAYS + " days");
        }
    }
}
