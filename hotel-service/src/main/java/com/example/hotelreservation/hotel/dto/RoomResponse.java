package com.example.hotelreservation.hotel.dto;

public record RoomResponse(
        Long id, Long hotelId, String hotelName, String number, boolean available, long timesBooked) {}
