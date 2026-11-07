package com.example.hotelreservation.booking.booking.controller;

import com.example.hotelreservation.booking.booking.dto.BookingResponse;
import com.example.hotelreservation.booking.booking.dto.CreateBookingRequest;
import com.example.hotelreservation.booking.booking.service.BookingService;
import com.example.hotelreservation.booking.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Hotel room bookings")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a booking, optionally auto-selecting a room")
    public BookingResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateBookingRequest request
    ) {
        return bookingService.create(CurrentUser.from(jwt), jwt.getTokenValue(), request);
    }

    @GetMapping
    @Operation(summary = "List bookings: own for a user, all for an admin")
    public List<BookingResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return bookingService.list(CurrentUser.from(jwt));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a booking by id")
    public BookingResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return bookingService.get(CurrentUser.from(jwt), id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel a booking")
    public BookingResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return bookingService.cancel(CurrentUser.from(jwt), id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a booking (explicit action form)")
    public BookingResponse cancelAction(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return bookingService.cancel(CurrentUser.from(jwt), id);
    }
}
