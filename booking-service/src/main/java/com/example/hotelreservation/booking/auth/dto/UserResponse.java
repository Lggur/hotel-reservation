package com.example.hotelreservation.booking.auth.dto;

import java.util.List;

public record UserResponse(Long id, String username, List<String> roles) {}
