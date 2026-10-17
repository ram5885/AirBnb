package com.HotelManagement.AirBnb.auth.service;

import com.HotelManagement.AirBnb.auth.dto.AuthResponse;
import com.HotelManagement.AirBnb.auth.dto.LoginRequest;
import com.HotelManagement.AirBnb.auth.dto.SignupRequest;
import com.HotelManagement.AirBnb.auth.entity.User;
import com.HotelManagement.AirBnb.auth.exception.DuplicateEmailException;
import com.HotelManagement.AirBnb.auth.exception.InvalidCredentialsException;
import com.HotelManagement.AirBnb.auth.repository.UserRepository;
import com.HotelManagement.AirBnb.shared.logging.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            log.info("Signup refused: email already registered");
            AuditLogger.failure("SIGNUP", "email", request.getEmail(), "reason", "email already registered");
            throw new DuplicateEmailException("Email is already registered: " + request.getEmail());
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        // Never store the raw password - hash it with BCrypt (salted, one-way).
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRoles(Set.of(request.getRole()));

        User saved = userRepository.save(user);
        log.info("Created account {} with role {}", saved.getId(), request.getRole());
        AuditLogger.success("SIGNUP", "userId", saved.getId(), "email", saved.getEmail(), "role", request.getRole());

        return new AuthResponse(saved.getId(), saved.getName(), saved.getEmail(), saved.getRoles(), "Signup successful",
                jwtService.issueToken(saved));
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null) {
            log.info("Login failed: unknown email");
            AuditLogger.failure("LOGIN", "email", request.getEmail(), "reason", "unknown email");
            throw new InvalidCredentialsException("Invalid email or password");
        }

        // Compare the raw password against the stored BCrypt hash.
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.info("Login failed for account {}: wrong password", user.getId());
            AuditLogger.failure("LOGIN", "userId", user.getId(), "email", user.getEmail(), "reason", "wrong password");
            throw new InvalidCredentialsException("Invalid email or password");
        }

        log.info("Account {} logged in", user.getId());
        AuditLogger.success("LOGIN", "userId", user.getId(), "email", user.getEmail(), "roles", user.getRoles());
        return new AuthResponse(user.getId(), user.getName(), user.getEmail(), user.getRoles(), "Login successful",
                jwtService.issueToken(user));
    }
}
