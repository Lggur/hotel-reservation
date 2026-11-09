package com.example.hotelreservation.hotel.mapper;

import com.example.hotelreservation.hotel.dto.RoomRequest;
import com.example.hotelreservation.hotel.dto.RoomResponse;
import com.example.hotelreservation.hotel.entity.Room;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface RoomMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "hotel", ignore = true)
    @Mapping(target = "available", ignore = true)
    @Mapping(target = "timesBooked", ignore = true)
    Room toEntity(RoomRequest request);

    @Mapping(target = "hotelId", source = "hotel.id")
    @Mapping(target = "hotelName", source = "hotel.name")
    RoomResponse toResponse(Room room);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "hotel", ignore = true)
    @Mapping(target = "available", ignore = true)
    @Mapping(target = "timesBooked", ignore = true)
    void update(RoomRequest request, @MappingTarget Room room);
}
