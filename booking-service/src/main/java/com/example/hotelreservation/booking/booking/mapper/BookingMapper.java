package com.example.hotelreservation.booking.booking.mapper;

import com.example.hotelreservation.booking.booking.dto.BookingResponse;
import com.example.hotelreservation.booking.booking.entity.Booking;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BookingMapper {

    BookingResponse toResponse(Booking booking);
}
