package com.HotelManagement.AirBnb.guest.service;

import com.HotelManagement.AirBnb.auth.entity.User;
import com.HotelManagement.AirBnb.guest.dto.GuestRequest;
import com.HotelManagement.AirBnb.guest.dto.GuestResponse;
import com.HotelManagement.AirBnb.guest.entity.Guest;
import com.HotelManagement.AirBnb.guest.repository.GuestRepository;
import com.HotelManagement.AirBnb.shared.exception.ResourceNotFoundException;
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
