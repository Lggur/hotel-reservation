package com.example.hotelreservation.booking.booking.controller;

import com.example.hotelreservation.booking.booking.dto.BookingResponse;
import com.example.hotelreservation.booking.booking.dto.CreateBookingRequest;
import com.example.hotelreservation.booking.booking.dto.PageResponse;
import com.example.hotelreservation.booking.booking.service.BookingService;
import com.example.hotelreservation.booking.exception.BadRequestException;
import com.example.hotelreservation.booking.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Hotel room bookings")
public class BookingController {

    private static final int MAX_PAGE_SIZE = 100;

    private final BookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a booking, optionally auto-selecting a room")
    public BookingResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateBookingRequest request,
            @Parameter(description = "Optional key that makes repeated requests return the same booking")
                    @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey) {
        return bookingService.create(CurrentUser.from(jwt), jwt.getTokenValue(), request, idempotencyKey);
    }

    @GetMapping
    @Operation(summary = "List bookings: own for a user, all for an admin")
    public PageResponse<BookingResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return bookingService.list(CurrentUser.from(jwt), pageable(page, size));
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

    private Pageable pageable(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    }
}
