package com.example.hotelreservation.booking.exception;

public class HotelServiceErrorException extends RuntimeException {

    public HotelServiceErrorException(String message, Throwable cause) {
        super(message, cause);
    }
}
