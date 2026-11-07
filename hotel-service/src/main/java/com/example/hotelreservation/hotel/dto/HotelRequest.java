package com.example.hotelreservation.hotel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HotelRequest(
        @NotBlank(message = "name must not be blank")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        @NotBlank(message = "address must not be blank")
        @Size(max = 512, message = "address must be at most 512 characters")
        String address
) {
}
