package com.example.hotelreservation.hotel.repository;

import com.example.hotelreservation.hotel.entity.ReservationStatus;
import com.example.hotelreservation.hotel.entity.Room;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findAllByOrderByIdAsc();

    List<Room> findAllByHotelIdOrderByIdAsc(Long hotelId);

    List<Room> findAllByAvailableTrueOrderByIdAsc();

    List<Room> findAllByAvailableTrueAndHotelIdOrderByIdAsc(Long hotelId);

    boolean existsByHotelId(Long hotelId);

    boolean existsByHotelIdAndNumberIgnoreCase(Long hotelId, String number);

    boolean existsByHotelIdAndNumberIgnoreCaseAndIdNot(Long hotelId, String number, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select r
            from Room r
            where r.available = true
              and (:hotelId is null or r.hotel.id = :hotelId)
              and not exists (
                  select rr.id
                  from RoomReservation rr
                  where rr.room = r
                    and rr.status = :status
                    and rr.startDate < :endDate
                    and rr.endDate > :startDate
              )
            order by r.id asc
            """)
    List<Room> findAvailableForDates(
            @Param("hotelId") Long hotelId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") ReservationStatus status
    );

    @Query("""
            select r
            from Room r
            where r.available = true
              and (:hotelId is null or r.hotel.id = :hotelId)
              and not exists (
                  select rr.id
                  from RoomReservation rr
                  where rr.room = r
                    and rr.status = :status
                    and rr.startDate < :endDate
                    and rr.endDate > :startDate
              )
            order by r.timesBooked asc, r.id asc
            """)
    List<Room> findRecommendedForDates(
            @Param("hotelId") Long hotelId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") ReservationStatus status
    );
}
