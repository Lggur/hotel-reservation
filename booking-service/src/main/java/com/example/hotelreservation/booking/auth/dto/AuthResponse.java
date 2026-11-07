package com.example.hotelreservation.booking.auth.dto;

import java.util.List;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresIn,
        Long userId,
        String username,
        List<String> roles
) {
}
