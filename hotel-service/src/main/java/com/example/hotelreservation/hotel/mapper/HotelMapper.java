package com.example.hotelreservation.hotel.mapper;

import com.example.hotelreservation.hotel.dto.HotelRequest;
import com.example.hotelreservation.hotel.dto.HotelResponse;
import com.example.hotelreservation.hotel.entity.Hotel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface HotelMapper {
  @Mapping(target = "id", ignore = true)
  Hotel toEntity(HotelRequest request);

  HotelResponse toResponse(Hotel hotel);

  @Mapping(target = "id", ignore = true)
  void update(HotelRequest request, @MappingTarget Hotel hotel);
}
