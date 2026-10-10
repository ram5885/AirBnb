package com.HotelManagement.AirBnb.booking.service;

import com.HotelManagement.AirBnb.booking.entity.Booking;
import com.HotelManagement.AirBnb.hotel.entity.Inventory;
import com.HotelManagement.AirBnb.hotel.repository.InventoryRepository;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Takes and returns a booking's rooms in the date-wise inventory. Rows are locked in date order
 * (same as BookingService) so concurrent operations can't deadlock or oversell; the inventory check
 * constraint (0 <= booked_count <= total_count) is the final guard. Callers must hold the booking's
 * row lock and check its status first, which is what makes each release happen exactly once.
 */
@Component
public class BookingInventory {

    private final InventoryRepository inventoryRepository;

    public BookingInventory(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public void release(Booking booking) {
        for (Inventory inv : lockNights(booking)) {
            inv.setBookedCount(Math.max(0, inv.getBookedCount() - booking.getRoomsCount()));
        }
    }

    /** Re-takes the rooms of a booking that had released them (late payment after expiry); false if unavailable. */
    public boolean reacquire(Booking booking) {
        List<Inventory> nights = lockNights(booking);
        long expected = ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        if (nights.size() != expected) {
            return false;
        }
        for (Inventory inv : nights) {
            if (Boolean.TRUE.equals(inv.getClosed()) || inv.getTotalCount() - inv.getBookedCount() < booking.getRoomsCount()) {
                return false;
            }
        }
        nights.forEach(inv -> inv.setBookedCount(inv.getBookedCount() + booking.getRoomsCount()));
        return true;
    }

    private List<Inventory> lockNights(Booking booking) {
        return inventoryRepository.findRangeForUpdate(booking.getRoom().getId(), booking.getCheckInDate(),
                booking.getCheckOutDate());
    }
}
