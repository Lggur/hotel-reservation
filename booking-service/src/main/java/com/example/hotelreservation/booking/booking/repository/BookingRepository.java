package com.example.hotelreservation.booking.booking.repository;

import com.example.hotelreservation.booking.booking.entity.Booking;
import com.example.hotelreservation.booking.booking.entity.BookingStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findAllByUserIdOrderByIdDesc(Long userId);

    List<Booking> findAllByOrderByIdDesc();

    Page<Booking> findAllByUserId(Long userId, Pageable pageable);

    Optional<Booking> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    List<Booking> findByStatusAndCreatedAtBeforeOrderByIdAsc(BookingStatus status, Instant threshold);
}
