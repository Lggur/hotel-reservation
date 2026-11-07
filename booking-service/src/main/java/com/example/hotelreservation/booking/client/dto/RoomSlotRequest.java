package com.example.hotelreservation.booking.client.dto;

public record RoomSlotRequest(
        Long bookingId,
        String requestId,
        String startDate,
        String endDate
) {
}
