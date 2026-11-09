package com.example.hotelreservation.booking.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "username must not be blank")
                @Size(min = 3, max = 64, message = "username must be between 3 and 64 characters")
                String username,
        @NotBlank(message = "password must not be blank")
                @Size(min = 6, max = 128, message = "password must be between 6 and 128 characters")
                String password) {}
