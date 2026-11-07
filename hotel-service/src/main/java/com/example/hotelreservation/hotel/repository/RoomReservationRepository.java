package com.example.hotelreservation.hotel.repository;

import com.example.hotelreservation.hotel.entity.ReservationStatus;
import com.example.hotelreservation.hotel.entity.RoomReservation;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomReservationRepository extends JpaRepository<RoomReservation, Long> {

    Optional<RoomReservation> findByBookingId(Long bookingId);

    @Query("""
            select count(r)
            from RoomReservation r
            where r.room.id = :roomId
              and r.status = :status
              and r.startDate < :endDate
              and r.endDate > :startDate
            """)
    long countOverlapping(
            @Param("roomId") Long roomId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") ReservationStatus status
    );

    @Query("""
            select r
            from RoomReservation r
            where r.room.id in :roomIds
              and r.status = :status
              and r.startDate < :to
              and r.endDate > :from
            """)
    List<RoomReservation> findInRange(
            @Param("roomIds") Collection<Long> roomIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") ReservationStatus status
    );
}
