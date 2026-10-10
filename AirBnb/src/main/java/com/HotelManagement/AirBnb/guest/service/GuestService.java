package com.HotelManagement.AirBnb.service;

import com.HotelManagement.AirBnb.dto.guest.GuestRequest;
import com.HotelManagement.AirBnb.dto.guest.GuestResponse;
import com.HotelManagement.AirBnb.entity.Guest;
import com.HotelManagement.AirBnb.entity.User;
import com.HotelManagement.AirBnb.exception.ResourceNotFoundException;
import com.HotelManagement.AirBnb.repository.GuestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** The current user's saved guests (people who travel on their bookings). */
@Service
public class GuestService {

    private final GuestRepository guestRepository;

    public GuestService(GuestRepository guestRepository) {
        this.guestRepository = guestRepository;
    }

    @Transactional(readOnly = true)
    public List<GuestResponse> list(User user) {
        return guestRepository.findByUser(user.getId()).stream().map(GuestResponse::from).toList();
    }

    @Transactional
    public GuestResponse create(User user, GuestRequest request) {
        Guest guest = new Guest();
        guest.setUser(user);
        apply(guest, request);
        return GuestResponse.from(guestRepository.save(guest));
    }

    @Transactional
    public GuestResponse update(User user, Long guestId, GuestRequest request) {
        Guest guest = guestRepository.findByIdForUser(guestId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Guest not found"));
        apply(guest, request);
        return GuestResponse.from(guest);
    }

    private static void apply(Guest guest, GuestRequest request) {
        guest.setName(request.name().strip());
        guest.setGender(request.gender());
        guest.setAge(request.age());
    }
}
