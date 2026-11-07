package com.example.hotelreservation.hotel.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AvailabilityRequest(
  @NotNull(message = "bookingId must not be null")
  @Positive(message = "bookingId must be positive")
  Long bookingId,

  @Size(max = 128, message = "requestId must be at most 128 characters")
  String requestId,

  @NotNull(message = "startDate must not be null") LocalDate startDate,

  @NotNull(message = "endDate must not be null") LocalDate endDate
) {}
