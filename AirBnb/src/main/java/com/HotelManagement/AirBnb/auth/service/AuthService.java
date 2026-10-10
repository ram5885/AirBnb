package com.HotelManagement.AirBnb.auth.service;

import com.HotelManagement.AirBnb.auth.dto.AuthResponse;
import com.HotelManagement.AirBnb.auth.dto.LoginRequest;
import com.HotelManagement.AirBnb.auth.dto.SignupRequest;
import com.HotelManagement.AirBnb.auth.entity.User;
import com.HotelManagement.AirBnb.auth.exception.DuplicateEmailException;
import com.HotelManagement.AirBnb.auth.exception.InvalidCredentialsException;
import com.HotelManagement.AirBnb.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AuthService {

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
            throw new DuplicateEmailException("Email is already registered: " + request.getEmail());
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        // Never store the raw password - hash it with BCrypt (salted, one-way).
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRoles(Set.of(request.getRole()));

        User saved = userRepository.save(user);

        return new AuthResponse(saved.getId(), saved.getName(), saved.getEmail(), saved.getRoles(), "Signup successful",
                jwtService.issueToken(saved));
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        // Compare the raw password against the stored BCrypt hash.
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        return new AuthResponse(user.getId(), user.getName(), user.getEmail(), user.getRoles(), "Login successful",
                jwtService.issueToken(user));
    }
}
