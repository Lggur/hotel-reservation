package com.example.hotelreservation.booking.exception;

public class HotelServiceUnavailableException extends RuntimeException {

    public HotelServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public HotelServiceUnavailableException(String message) {
        super(message);
    }
}
