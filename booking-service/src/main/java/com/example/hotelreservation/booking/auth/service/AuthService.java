package com.example.hotelreservation.booking.auth.service;

import com.example.hotelreservation.booking.auth.dto.AuthResponse;
import com.example.hotelreservation.booking.auth.dto.LoginRequest;
import com.example.hotelreservation.booking.auth.dto.RegisterRequest;
import com.example.hotelreservation.booking.auth.dto.UserResponse;
import com.example.hotelreservation.booking.auth.entity.AppUser;
import com.example.hotelreservation.booking.auth.entity.Role;
import com.example.hotelreservation.booking.auth.repository.AppUserRepository;
import com.example.hotelreservation.booking.exception.ConflictException;
import com.example.hotelreservation.booking.exception.UnauthorizedException;
import com.example.hotelreservation.booking.security.JwtService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("Username " + username + " is already taken");
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        user.setCreatedAt(Instant.now());

        return toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        AppUser user = userRepository
                .findByUsernameIgnoreCase(request.username().trim())
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid username or password");
        }

        return new AuthResponse(
                jwtService.issue(user),
                "Bearer",
                jwtService.expiresInSeconds(),
                user.getId(),
                user.getUsername(),
                List.of(user.getRole().name()));
    }

    private UserResponse toResponse(AppUser user) {
        return new UserResponse(
                user.getId(), user.getUsername(), List.of(user.getRole().name()));
    }
}
