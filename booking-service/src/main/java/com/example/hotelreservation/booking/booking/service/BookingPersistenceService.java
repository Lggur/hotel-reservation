package com.example.hotelreservation.booking.booking.service;

import com.example.hotelreservation.booking.booking.entity.Booking;
import com.example.hotelreservation.booking.booking.entity.BookingStatus;
import com.example.hotelreservation.booking.booking.repository.BookingRepository;
import com.example.hotelreservation.booking.exception.ConflictException;
import com.example.hotelreservation.booking.exception.ResourceNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingPersistenceService {

    private final BookingRepository bookingRepository;

    @Transactional
    public Booking createPending(Long userId, Long hotelId, Long roomId, LocalDate startDate, LocalDate endDate) {
        return createPending(userId, hotelId, roomId, startDate, endDate, null);
    }

    @Transactional
    public Booking createPending(
            Long userId, Long hotelId, Long roomId, LocalDate startDate, LocalDate endDate, String idempotencyKey) {
        Instant now = Instant.now();
        Booking booking = new Booking();
        booking.setUserId(userId);
        booking.setHotelId(hotelId);
        booking.setRoomId(roomId);
        booking.setStartDate(startDate);
        booking.setEndDate(endDate);
        booking.setStatus(BookingStatus.PENDING);
        booking.setIdempotencyKey(idempotencyKey);
        booking.setCreatedAt(now);
        booking.setUpdatedAt(now);
        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking markConfirmed(Long bookingId) {
        Booking booking = requireBooking(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ConflictException(
                    "Booking " + bookingId + " cannot be confirmed from status " + booking.getStatus());
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setUpdatedAt(Instant.now());
        return booking;
    }

    @Transactional
    public Booking markCancelled(Long bookingId, String reason) {
        Booking booking = requireBooking(bookingId);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return booking;
        }
        booking.setStatus(BookingStatus.CANCELLED);
        if (reason != null) {
            booking.setFailureReason(reason);
        }
        booking.setUpdatedAt(Instant.now());
        return booking;
    }

    @Transactional
    public Optional<Booking> markCancelledIfPending(Long bookingId, String reason) {
        Optional<Booking> found = bookingRepository.findById(bookingId);
        if (found.isEmpty() || found.get().getStatus() != BookingStatus.PENDING) {
            return Optional.empty();
        }

        Booking booking = found.get();
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setFailureReason(reason);
        booking.setUpdatedAt(Instant.now());
        return Optional.of(booking);
    }

    @Transactional(readOnly = true)
    public List<Booking> findStalePending(Duration timeout) {
        return bookingRepository.findByStatusAndCreatedAtBeforeOrderByIdAsc(
                BookingStatus.PENDING, Instant.now().minus(timeout));
    }

    @Transactional(readOnly = true)
    public Booking get(Long bookingId) {
        return requireBooking(bookingId);
    }

    @Transactional(readOnly = true)
    public Optional<Booking> findByIdempotencyKey(Long userId, String idempotencyKey) {
        return bookingRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
    }

    @Transactional(readOnly = true)
    public Page<Booking> pageForUser(Long userId, Pageable pageable) {
        return bookingRepository.findAllByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Booking> pageAll(Pageable pageable) {
        return bookingRepository.findAll(pageable);
    }

    private Booking requireBooking(Long bookingId) {
        return bookingRepository
                .findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + bookingId + " not found"));
    }
}
