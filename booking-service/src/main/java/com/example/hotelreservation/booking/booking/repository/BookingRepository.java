package com.example.hotelreservation.booking.booking.repository;

import com.example.hotelreservation.booking.booking.entity.Booking;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findAllByUserIdOrderByIdDesc(Long userId);

    List<Booking> findAllByOrderByIdDesc();
}
