package com.example.hotelreservation.hotel.repository;

import com.example.hotelreservation.hotel.entity.Room;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findAllByOrderByIdAsc();

    List<Room> findAllByHotelIdOrderByIdAsc(Long hotelId);

    boolean existsByHotelId(Long hotelId);

    boolean existsByHotelIdAndNumberIgnoreCase(Long hotelId, String number);

    boolean existsByHotelIdAndNumberIgnoreCaseAndIdNot(Long hotelId, String number, Long id);
}
