package com.example.hotelreservation.hotel.dto;

public record RoomStatisticsResponse(
  Long roomId,
  Long hotelId,
  String number,
  long timesBooked,
  long activeReservations,
  long occupiedNights,
  double occupancyRate
) {}
