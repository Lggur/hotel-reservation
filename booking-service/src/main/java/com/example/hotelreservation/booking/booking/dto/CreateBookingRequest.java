package com.example.hotelreservation.booking.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record CreateBookingRequest(
        @NotNull(message = "hotelId must not be null") @Positive(message = "hotelId must be positive") Long hotelId,
        @Positive(message = "roomId must be positive") Long roomId,
        @NotNull(message = "startDate must not be null") LocalDate startDate,
        @NotNull(message = "endDate must not be null") LocalDate endDate) {}
