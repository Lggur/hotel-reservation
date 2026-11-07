package com.example.hotelreservation.hotel.controller;

import com.example.hotelreservation.hotel.dto.AvailabilityRequest;
import com.example.hotelreservation.hotel.dto.AvailabilityResponse;
import com.example.hotelreservation.hotel.dto.ReleaseRequest;
import com.example.hotelreservation.hotel.dto.RoomRequest;
import com.example.hotelreservation.hotel.dto.RoomResponse;
import com.example.hotelreservation.hotel.dto.StatisticsResponse;
import com.example.hotelreservation.hotel.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Tag(name = "Rooms", description = "Room management, availability and statistics")
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a room")
    public RoomResponse create(@Valid @RequestBody RoomRequest request) {
        return roomService.create(request);
    }

    @GetMapping
    @Operation(summary = "List available rooms, optionally filtered by hotel and stay dates")
    public List<RoomResponse> findAll(
            @RequestParam(required = false) Long hotelId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return roomService.findAvailable(hotelId, startDate, endDate);
    }

    @GetMapping("/recommend")
    @Operation(summary = "Recommend available rooms ordered by booking count")
    public List<RoomResponse> recommend(
            @RequestParam(required = false) Long hotelId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return roomService.recommend(hotelId, startDate, endDate);
    }

    @GetMapping("/statistics")
    @Operation(summary = "Room occupancy statistics for a period")
    public StatisticsResponse statistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long hotelId
    ) {
        return roomService.statistics(hotelId, from, to);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a room by id")
    public RoomResponse findById(@PathVariable Long id) {
        return roomService.findById(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a room")
    public RoomResponse update(@PathVariable Long id, @Valid @RequestBody RoomRequest request) {
        return roomService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a room")
    public void delete(@PathVariable Long id) {
        roomService.delete(id);
    }

    @PostMapping("/{id}/confirm-availability")
    @Operation(summary = "Confirm room availability for a booking (internal)")
    public AvailabilityResponse confirmAvailability(
            @PathVariable Long id,
            @Valid @RequestBody AvailabilityRequest request
    ) {
        return roomService.confirmAvailability(id, request);
    }

    @PostMapping("/{id}/release")
    @Operation(summary = "Release a previously confirmed room slot (internal)")
    public AvailabilityResponse release(
            @PathVariable Long id,
            @Valid @RequestBody ReleaseRequest request
    ) {
        return roomService.release(id, request);
    }
}
