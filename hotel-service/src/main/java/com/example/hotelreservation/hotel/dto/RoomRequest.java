package com.example.hotelreservation.hotel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RoomRequest(
  @NotNull(message = "hotelId must not be null")
  @Positive(message = "hotelId must be positive")
  Long hotelId,

  @NotBlank(message = "number must not be blank")
  @Size(max = 32, message = "number must be at most 32 characters")
  String number,

  Boolean available
) {}
