package com.example.hotelreservation.hotel.service;

import com.example.hotelreservation.hotel.dto.AvailabilityRequest;
import com.example.hotelreservation.hotel.dto.AvailabilityResponse;
import com.example.hotelreservation.hotel.dto.ReleaseRequest;
import com.example.hotelreservation.hotel.dto.RoomRequest;
import com.example.hotelreservation.hotel.dto.RoomResponse;
import com.example.hotelreservation.hotel.dto.RoomStatisticsResponse;
import com.example.hotelreservation.hotel.dto.StatisticsResponse;
import com.example.hotelreservation.hotel.entity.Hotel;
import com.example.hotelreservation.hotel.entity.ReservationStatus;
import com.example.hotelreservation.hotel.entity.Room;
import com.example.hotelreservation.hotel.entity.RoomReservation;
import com.example.hotelreservation.hotel.exception.BadRequestException;
import com.example.hotelreservation.hotel.exception.ConflictException;
import com.example.hotelreservation.hotel.exception.ResourceNotFoundException;
import com.example.hotelreservation.hotel.mapper.RoomMapper;
import com.example.hotelreservation.hotel.repository.HotelRepository;
import com.example.hotelreservation.hotel.repository.RoomRepository;
import com.example.hotelreservation.hotel.repository.RoomReservationRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomService {

    private static final ReservationStatus ACTIVE = ReservationStatus.ACTIVE;
    private static final ReservationStatus RELEASED = ReservationStatus.RELEASED;

    private final RoomRepository roomRepository;
    private final RoomReservationRepository reservationRepository;
    private final HotelRepository hotelRepository;
    private final RoomMapper roomMapper;

    @Transactional
    public RoomResponse create(RoomRequest request) {
        Hotel hotel = requireHotel(request.hotelId());
        ensureNumberAvailable(request.hotelId(), request.number(), null);

        Room room = roomMapper.toEntity(request);
        room.setHotel(hotel);
        room.setAvailable(request.available() == null || request.available());

        return roomMapper.toResponse(roomRepository.save(room));
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> findAvailable(Long hotelId, LocalDate startDate, LocalDate endDate) {
        List<Room> rooms;
        if (startDate == null && endDate == null) {
            rooms = hotelId == null
                    ? roomRepository.findAllByAvailableTrueOrderByIdAsc()
                    : roomRepository.findAllByAvailableTrueAndHotelIdOrderByIdAsc(hotelId);
        } else {
            validateRange(startDate, endDate);
            rooms = roomRepository.findAvailableForDates(hotelId, startDate, endDate, ACTIVE);
        }

        return toResponses(rooms);
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> recommend(Long hotelId, LocalDate startDate, LocalDate endDate) {
        validateRange(startDate, endDate);
        return toResponses(roomRepository.findRecommendedForDates(hotelId, startDate, endDate, ACTIVE));
    }

    @Transactional(readOnly = true)
    public RoomResponse findById(Long id) {
        return roomMapper.toResponse(requireRoom(id));
    }

    @Transactional
    public RoomResponse update(Long id, RoomRequest request) {
        Room room = requireRoom(id);
        Hotel hotel = requireHotel(request.hotelId());
        ensureNumberAvailable(request.hotelId(), request.number(), id);

        roomMapper.update(request, room);
        room.setHotel(hotel);
        if (request.available() != null) {
            room.setAvailable(request.available());
        }

        return roomMapper.toResponse(room);
    }

    @Transactional
    public void delete(Long id) {
        roomRepository.delete(requireRoom(id));
    }

    @Transactional
    public AvailabilityResponse confirmAvailability(Long roomId, AvailabilityRequest request) {
        validateRange(request.startDate(), request.endDate());

        Room room = lockRoom(roomId);
        if (!room.isAvailable()) {
            throw new ConflictException("Room " + roomId + " is not available");
        }

        Optional<RoomReservation> existing = reservationRepository.findByBookingId(request.bookingId());
        if (existing.isPresent()) {
            return idempotentConfirm(room, existing.get(), request);
        }

        if (reservationRepository.countOverlapping(roomId, request.startDate(), request.endDate(), ACTIVE) > 0) {
            throw new ConflictException("Room " + roomId + " is not available for the requested dates");
        }

        RoomReservation reservation = new RoomReservation();
        reservation.setRoom(room);
        reservation.setBookingId(request.bookingId());
        reservation.setRequestId(request.requestId());
        reservation.setStartDate(request.startDate());
        reservation.setEndDate(request.endDate());
        reservation.setStatus(ACTIVE);
        reservation.setCreatedAt(Instant.now());
        reservationRepository.save(reservation);

        room.setTimesBooked(room.getTimesBooked() + 1);
        return new AvailabilityResponse(roomId, request.bookingId(), ACTIVE.name(), room.getTimesBooked());
    }

    @Transactional
    public AvailabilityResponse release(Long roomId, ReleaseRequest request) {
        if (request.startDate() != null || request.endDate() != null) {
            validateRange(request.startDate(), request.endDate());
        }

        Room room = lockRoom(roomId);
        Optional<RoomReservation> existing = reservationRepository.findByBookingId(request.bookingId());

        if (existing.isEmpty()) {
            RoomReservation tombstone = new RoomReservation();
            tombstone.setRoom(room);
            tombstone.setBookingId(request.bookingId());
            tombstone.setRequestId(request.requestId());
            tombstone.setStartDate(request.startDate());
            tombstone.setEndDate(request.endDate());
            tombstone.setStatus(RELEASED);
            tombstone.setCreatedAt(Instant.now());
            reservationRepository.save(tombstone);
            return new AvailabilityResponse(roomId, request.bookingId(), RELEASED.name(), room.getTimesBooked());
        }

        RoomReservation reservation = existing.get();
        if (!reservation.getRoom().getId().equals(room.getId())) {
            throw new ConflictException("Booking " + request.bookingId() + " is associated with another room");
        }

        if (reservation.getStatus() == ACTIVE) {
            reservation.setStatus(RELEASED);
            if (room.getTimesBooked() > 0) {
                room.setTimesBooked(room.getTimesBooked() - 1);
            }
        }

        return new AvailabilityResponse(roomId, request.bookingId(), RELEASED.name(), room.getTimesBooked());
    }

    @Transactional(readOnly = true)
    public StatisticsResponse statistics(Long hotelId, LocalDate from, LocalDate to) {
        validateRange(from, to);

        List<Room> rooms = hotelId == null
                ? roomRepository.findAllByOrderByIdAsc()
                : roomRepository.findAllByHotelIdOrderByIdAsc(hotelId);
        List<Long> roomIds = rooms.stream().map(Room::getId).toList();

        List<RoomReservation> reservations = roomIds.isEmpty()
                ? List.of()
                : reservationRepository.findInRange(roomIds, from, to, ACTIVE);

        Map<Long, List<RoomReservation>> byRoom = reservations.stream()
                .collect(Collectors.groupingBy(reservation -> reservation.getRoom().getId()));

        long totalNights = ChronoUnit.DAYS.between(from, to);
        List<RoomStatisticsResponse> roomStatistics = rooms.stream()
                .map(room -> toStatistics(room, byRoom.getOrDefault(room.getId(), List.of()), from, to, totalNights))
                .toList();
        long totalTimesBooked = rooms.stream().mapToLong(Room::getTimesBooked).sum();

        return new StatisticsResponse(from, to, rooms.size(), totalTimesBooked, roomStatistics);
    }

    private AvailabilityResponse idempotentConfirm(
            Room room,
            RoomReservation reservation,
            AvailabilityRequest request
    ) {
        if (!reservation.getRoom().getId().equals(room.getId())) {
            throw new ConflictException(
                    "Booking " + request.bookingId() + " is already associated with another room");
        }
        if (reservation.getStatus() == RELEASED) {
            throw new ConflictException("Booking " + request.bookingId() + " was already released");
        }
        if (!reservation.getStartDate().equals(request.startDate())
                || !reservation.getEndDate().equals(request.endDate())) {
            throw new ConflictException(
                    "Booking " + request.bookingId() + " was already confirmed for different dates");
        }
        return new AvailabilityResponse(room.getId(), request.bookingId(), ACTIVE.name(), room.getTimesBooked());
    }

    private RoomStatisticsResponse toStatistics(
            Room room,
            List<RoomReservation> reservations,
            LocalDate from,
            LocalDate to,
            long totalNights
    ) {
        long occupiedNights = reservations.stream()
                .mapToLong(reservation -> nightsInRange(reservation, from, to))
                .sum();
        double occupancyRate = totalNights == 0
                ? 0.0
                : round4((double) occupiedNights / totalNights);

        return new RoomStatisticsResponse(
                room.getId(),
                room.getHotel().getId(),
                room.getNumber(),
                room.getTimesBooked(),
                reservations.size(),
                occupiedNights,
                occupancyRate
        );
    }

    private long nightsInRange(RoomReservation reservation, LocalDate from, LocalDate to) {
        LocalDate start = reservation.getStartDate().isBefore(from) ? from : reservation.getStartDate();
        LocalDate end = reservation.getEndDate().isAfter(to) ? to : reservation.getEndDate();
        return Math.max(0, ChronoUnit.DAYS.between(start, end));
    }

    private double round4(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private Room lockRoom(Long roomId) {
        return roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room " + roomId + " not found"));
    }

    private void validateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BadRequestException("startDate and endDate are required");
        }
        if (!startDate.isBefore(endDate)) {
            throw new BadRequestException("startDate must be before endDate");
        }
    }

    private List<RoomResponse> toResponses(List<Room> rooms) {
        return rooms.stream()
                .map(roomMapper::toResponse)
                .toList();
    }

    private Room requireRoom(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room " + id + " not found"));
    }

    private Hotel requireHotel(Long id) {
        return hotelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel " + id + " not found"));
    }

    private void ensureNumberAvailable(Long hotelId, String number, Long excludedRoomId) {
        boolean exists = excludedRoomId == null
                ? roomRepository.existsByHotelIdAndNumberIgnoreCase(hotelId, number)
                : roomRepository.existsByHotelIdAndNumberIgnoreCaseAndIdNot(hotelId, number, excludedRoomId);

        if (exists) {
            throw new ConflictException("Room number " + number + " already exists in hotel " + hotelId);
        }
    }
}
