package com.example.hotelreservation.hotel.config;

import com.example.hotelreservation.hotel.entity.Hotel;
import com.example.hotelreservation.hotel.entity.ReservationStatus;
import com.example.hotelreservation.hotel.entity.Room;
import com.example.hotelreservation.hotel.entity.RoomReservation;
import com.example.hotelreservation.hotel.repository.HotelRepository;
import com.example.hotelreservation.hotel.repository.RoomRepository;
import com.example.hotelreservation.hotel.repository.RoomReservationRepository;
import java.time.Instant;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class DemoDataInitializer implements ApplicationRunner {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final RoomReservationRepository reservationRepository;

    public DemoDataInitializer(
            HotelRepository hotelRepository,
            RoomRepository roomRepository,
            RoomReservationRepository reservationRepository) {
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
        this.reservationRepository = reservationRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (hotelRepository.count() > 0) {
            return;
        }

        Hotel grandPlaza = hotelRepository.save(hotel("Grand Plaza", "Tverskaya 12, Moscow"));
        Hotel seaside = hotelRepository.save(hotel("Seaside Resort", "Primorskaya 4, Sochi"));

        Room grand101 = roomRepository.save(room(grandPlaza, "101", 1));
        roomRepository.save(room(grandPlaza, "102", 4));
        roomRepository.save(room(grandPlaza, "103", 9));
        roomRepository.save(room(seaside, "201", 0));
        roomRepository.save(room(seaside, "202", 6));

        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = LocalDate.now().plusDays(4);

        RoomReservation reservation = new RoomReservation();
        reservation.setRoom(grand101);
        reservation.setBookingId(1L);
        reservation.setRequestId("demo-seed");
        reservation.setStartDate(start);
        reservation.setEndDate(end);
        reservation.setStatus(ReservationStatus.ACTIVE);
        reservation.setCreatedAt(Instant.now());
        reservationRepository.save(reservation);

        log.info("Demo data created: hotels=2 rooms=5 reservation={}..{}", start, end);
    }

    private Hotel hotel(String name, String address) {
        Hotel hotel = new Hotel();
        hotel.setName(name);
        hotel.setAddress(address);
        return hotel;
    }

    private Room room(Hotel hotel, String number, long timesBooked) {
        Room room = new Room();
        room.setHotel(hotel);
        room.setNumber(number);
        room.setAvailable(true);
        room.setTimesBooked(timesBooked);
        return room;
    }
}
