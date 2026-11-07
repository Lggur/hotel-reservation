package com.example.hotelreservation.booking.client.dto;

public record HotelRoom(
        Long id,
        Long hotelId,
        String number
) {
}
