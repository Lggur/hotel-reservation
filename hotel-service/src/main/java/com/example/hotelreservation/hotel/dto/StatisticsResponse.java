package com.example.hotelreservation.hotel.dto;

import java.time.LocalDate;
import java.util.List;

public record StatisticsResponse(
  LocalDate from,
  LocalDate to,
  int totalRooms,
  long totalTimesBooked,
  List<RoomStatisticsResponse> rooms
) {}
