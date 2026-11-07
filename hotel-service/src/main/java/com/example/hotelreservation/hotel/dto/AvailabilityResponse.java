package com.example.hotelreservation.hotel.dto;

public record AvailabilityResponse(
        Long roomId,
        Long bookingId,
        String status,
        long timesBooked
) {
}
