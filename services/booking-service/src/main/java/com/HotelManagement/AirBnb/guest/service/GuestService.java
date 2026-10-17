package com.HotelManagement.AirBnb.guest.service;

import com.HotelManagement.AirBnb.shared.logging.AuditLogger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.HotelManagement.AirBnb.shared.security.CurrentUser;
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

    private static final Logger log = LoggerFactory.getLogger(GuestService.class);

    private final GuestRepository guestRepository;

    public GuestService(GuestRepository guestRepository) {
        this.guestRepository = guestRepository;
    }

    @Transactional(readOnly = true)
    public List<GuestResponse> list(CurrentUser user) {
        return guestRepository.findByUser(user.getId()).stream().map(GuestResponse::from).toList();
    }

    @Transactional
    public GuestResponse create(CurrentUser user, GuestRequest request) {
        Guest guest = new Guest();
        guest.setUserId(user.getId());
        apply(guest, request);
        Guest saved = guestRepository.save(guest);
        log.info("User {} saved guest {}", user.getId(), saved.getId());
        AuditLogger.success("GUEST_PROFILE_CREATED", "guestId", saved.getId());
        return GuestResponse.from(saved);
    }

    @Transactional
    public GuestResponse update(CurrentUser user, Long guestId, GuestRequest request) {
        Guest guest = guestRepository.findByIdForUser(guestId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Guest not found"));
        apply(guest, request);
        log.info("User {} updated guest {}", user.getId(), guestId);
        AuditLogger.success("GUEST_PROFILE_UPDATED", "guestId", guestId);
        return GuestResponse.from(guest);
    }

    private static void apply(Guest guest, GuestRequest request) {
        guest.setName(request.name().strip());
        guest.setGender(request.gender());
        guest.setAge(request.age());
    }
}
