package com.example.hotelreservation.hotel.repository;

import com.example.hotelreservation.hotel.entity.Hotel;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HotelRepository extends JpaRepository<Hotel, Long> {

    List<Hotel> findAllByOrderByIdAsc();
}
