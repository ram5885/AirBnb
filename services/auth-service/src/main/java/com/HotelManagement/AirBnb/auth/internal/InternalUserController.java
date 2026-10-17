package com.HotelManagement.AirBnb.auth.internal;

import com.HotelManagement.AirBnb.auth.repository.UserRepository;
import com.HotelManagement.AirBnb.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Service-to-service user lookups (X-Internal-Token required, see InternalApiFilter). Used by the gateway
 * (session check: the token's user must still exist) and by booking/review services for names and emails.
 */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private static final Logger log = LoggerFactory.getLogger(InternalUserController.class);

    private final UserRepository userRepository;

    public InternalUserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 200 with the user, 404 when the account doesn't exist. */
    @GetMapping("/{id}")
    public UserView get(@PathVariable Long id) {
        log.debug("Internal lookup of user {}", id);
        return userRepository.findUserById(id).map(UserView::from)
                .orElseThrow(() -> new ResourceNotFoundException("User " + id + " not found"));
    }

    /** Users with these ids; unknown ids are simply absent. */
    @PostMapping("/lookup")
    public List<UserView> lookup(@Valid @RequestBody UserLookupRequest request) {
        log.debug("Internal lookup of {} user(s)", request.ids().size());
        if (request.ids().isEmpty()) {
            return List.of();
        }
        return userRepository.findUsersByIds(request.ids()).stream().map(UserView::from).toList();
    }
}
