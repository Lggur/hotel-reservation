package com.example.hotelreservation.booking.booking.controller;

import com.example.hotelreservation.booking.booking.dto.BookingResponse;
import com.example.hotelreservation.booking.booking.dto.RecoveryResponse;
import com.example.hotelreservation.booking.booking.service.BookingService;
import com.example.hotelreservation.booking.booking.service.StaleBookingRecoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/bookings")
@RequiredArgsConstructor
@Tag(name = "Internal bookings", description = "Service-to-service booking operations")
public class InternalBookingController {

    private final BookingService bookingService;
    private final StaleBookingRecoveryService recoveryService;

    @GetMapping("/{id}")
    @Operation(summary = "Get any booking (internal)")
    public BookingResponse get(@PathVariable Long id) {
        return bookingService.getInternal(id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel any booking (internal)")
    public BookingResponse cancel(@PathVariable Long id) {
        return bookingService.cancelInternal(id);
    }

    @PostMapping("/recover")
    @Operation(summary = "Recover stale pending bookings (internal)")
    public RecoveryResponse recover() {
        return new RecoveryResponse(recoveryService.recoverStaleBookings());
    }
}
