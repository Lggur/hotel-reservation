package com.example.hotelreservation.booking.booking.dto;

import com.example.hotelreservation.booking.booking.entity.BookingStatus;
import java.time.Instant;
import java.time.LocalDate;

public record BookingResponse(
        Long id,
        Long userId,
        Long hotelId,
        Long roomId,
        LocalDate startDate,
        LocalDate endDate,
        BookingStatus status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
}
