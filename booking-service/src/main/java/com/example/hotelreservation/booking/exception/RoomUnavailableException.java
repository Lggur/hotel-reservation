package com.example.hotelreservation.booking.exception;

public class RoomUnavailableException extends ConflictException {

    public RoomUnavailableException(String message) {
        super(message);
    }
}
