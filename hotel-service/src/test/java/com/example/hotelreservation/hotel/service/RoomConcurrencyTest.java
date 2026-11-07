package com.example.hotelreservation.hotel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.hotelreservation.hotel.dto.AvailabilityRequest;
import com.example.hotelreservation.hotel.entity.Hotel;
import com.example.hotelreservation.hotel.entity.ReservationStatus;
import com.example.hotelreservation.hotel.entity.Room;
import com.example.hotelreservation.hotel.exception.ConflictException;
import com.example.hotelreservation.hotel.repository.HotelRepository;
import com.example.hotelreservation.hotel.repository.RoomRepository;
import com.example.hotelreservation.hotel.repository.RoomReservationRepository;
import com.example.hotelreservation.hotel.security.TestJwt;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
  properties = {
    "eureka.client.enabled=false",
    "security.jwt.secret=" + TestJwt.SECRET,
    "spring.datasource.url=jdbc:h2:mem:concurrency-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
  }
)
class RoomConcurrencyTest {

  @Autowired
  private RoomService roomService;

  @Autowired
  private HotelRepository hotelRepository;

  @Autowired
  private RoomRepository roomRepository;

  @Autowired
  private RoomReservationRepository reservationRepository;

  private final ExecutorService executor = Executors.newFixedThreadPool(2);

  @AfterEach
  void cleanup() {
    executor.shutdownNow();
    reservationRepository.deleteAll();
    roomRepository.deleteAll();
    hotelRepository.deleteAll();
  }

  @Test
  void concurrentOverlappingConfirmsCreateOnlyOneSlot() throws Exception {
    long roomId = createRoom("101");
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);

    Future<Boolean> first = executor.submit(
      confirmTask(roomId, 7001L, ready, go, "2026-07-01", "2026-07-05")
    );
    Future<Boolean> second = executor.submit(
      confirmTask(roomId, 7002L, ready, go, "2026-07-03", "2026-07-07")
    );

    assertTrue(ready.await(5, TimeUnit.SECONDS));
    go.countDown();

    int successes =
      (first.get(10, TimeUnit.SECONDS) ? 1 : 0) +
      (second.get(10, TimeUnit.SECONDS) ? 1 : 0);

    assertEquals(1, successes);
    assertEquals(
      1L,
      roomRepository.findById(roomId).orElseThrow().getTimesBooked()
    );
    assertEquals(
      1L,
      reservationRepository.countOverlapping(
        roomId,
        LocalDate.parse("2026-07-01"),
        LocalDate.parse("2026-07-08"),
        ReservationStatus.ACTIVE
      )
    );
  }

  @Test
  void concurrentDuplicateConfirmsAreIdempotent() throws Exception {
    long roomId = createRoom("201");
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);

    Future<Boolean> first = executor.submit(
      confirmTask(roomId, 7100L, ready, go, "2026-07-10", "2026-07-12")
    );
    Future<Boolean> second = executor.submit(
      confirmTask(roomId, 7100L, ready, go, "2026-07-10", "2026-07-12")
    );

    assertTrue(ready.await(5, TimeUnit.SECONDS));
    go.countDown();

    assertTrue(first.get(10, TimeUnit.SECONDS));
    assertTrue(second.get(10, TimeUnit.SECONDS));
    assertEquals(
      1L,
      roomRepository.findById(roomId).orElseThrow().getTimesBooked()
    );
    assertEquals(
      1L,
      reservationRepository.countOverlapping(
        roomId,
        LocalDate.parse("2026-07-10"),
        LocalDate.parse("2026-07-12"),
        ReservationStatus.ACTIVE
      )
    );
  }

  private Callable<Boolean> confirmTask(
    long roomId,
    long bookingId,
    CountDownLatch ready,
    CountDownLatch go,
    String startDate,
    String endDate
  ) {
    return () -> {
      ready.countDown();
      if (!go.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Start signal was not received");
      }
      try {
        roomService.confirmAvailability(
          roomId,
          new AvailabilityRequest(
            bookingId,
            "req-" + bookingId,
            LocalDate.parse(startDate),
            LocalDate.parse(endDate)
          )
        );
        return true;
      } catch (ConflictException exception) {
        return false;
      }
    };
  }

  private long createRoom(String number) {
    Hotel hotel = new Hotel();
    hotel.setName("Hotel " + number);
    hotel.setAddress("Address");
    hotelRepository.save(hotel);

    Room room = new Room();
    room.setHotel(hotel);
    room.setNumber(number);
    room.setAvailable(true);
    return roomRepository.save(room).getId();
  }
}
