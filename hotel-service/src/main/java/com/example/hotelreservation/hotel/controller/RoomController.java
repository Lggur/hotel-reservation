package com.example.hotelreservation.hotel.controller;

import com.example.hotelreservation.hotel.dto.RoomRequest;
import com.example.hotelreservation.hotel.dto.RoomResponse;
import com.example.hotelreservation.hotel.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
@Tag(name = "Rooms", description = "Room management")
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a room")
    public RoomResponse create(@Valid @RequestBody RoomRequest request) {
        return roomService.create(request);
    }

    @GetMapping
    @Operation(summary = "List rooms, optionally filtered by hotel")
    public List<RoomResponse> findAll(@RequestParam(required = false) Long hotelId) {
        return roomService.findAll(hotelId);
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
}
