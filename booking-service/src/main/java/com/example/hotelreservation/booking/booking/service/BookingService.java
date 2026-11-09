package com.example.hotelreservation.booking.booking.service;

import com.example.hotelreservation.booking.booking.dto.BookingResponse;
import com.example.hotelreservation.booking.booking.dto.CreateBookingRequest;
import com.example.hotelreservation.booking.booking.dto.PageResponse;
import com.example.hotelreservation.booking.booking.entity.Booking;
import com.example.hotelreservation.booking.booking.entity.BookingStatus;
import com.example.hotelreservation.booking.booking.mapper.BookingMapper;
import com.example.hotelreservation.booking.client.HotelServiceClient;
import com.example.hotelreservation.booking.client.dto.HotelRoom;
import com.example.hotelreservation.booking.exception.BadRequestException;
import com.example.hotelreservation.booking.exception.ConflictException;
import com.example.hotelreservation.booking.exception.ForbiddenException;
import com.example.hotelreservation.booking.exception.HotelServiceErrorException;
import com.example.hotelreservation.booking.exception.HotelServiceUnavailableException;
import com.example.hotelreservation.booking.exception.RoomUnavailableException;
import com.example.hotelreservation.booking.exception.UnauthorizedException;
import com.example.hotelreservation.booking.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private static final String REASON_ROOM_UNAVAILABLE = "ROOM_UNAVAILABLE";
    private static final String REASON_HOTEL_UNAVAILABLE = "HOTEL_SERVICE_UNAVAILABLE";
    private static final String REASON_HOTEL_ERROR = "HOTEL_SERVICE_ERROR";
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    private final HotelServiceClient hotelServiceClient;
    private final BookingPersistenceService persistenceService;
    private final BookingMapper bookingMapper;

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public BookingResponse create(CurrentUser user, String token, CreateBookingRequest request) {
        return create(user, token, request, null);
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public BookingResponse create(CurrentUser user, String token, CreateBookingRequest request, String idempotencyKey) {
        Long userId = requireUserId(user);
        validateRange(request.startDate(), request.endDate());
        String key = normalizeIdempotencyKey(idempotencyKey);

        if (key != null) {
            Optional<Booking> existing = persistenceService.findByIdempotencyKey(userId, key);
            if (existing.isPresent()) {
                log.info(
                        "Replaying idempotent request {} for booking {}",
                        key,
                        existing.get().getId());
                return bookingMapper.toResponse(existing.get());
            }
        }

        Long roomId = selectRoom(user, token, request);
        Booking booking;
        try {
            booking = key == null
                    ? persistenceService.createPending(
                            userId, request.hotelId(), roomId, request.startDate(), request.endDate())
                    : persistenceService.createPending(
                            userId, request.hotelId(), roomId, request.startDate(), request.endDate(), key);
        } catch (DataIntegrityViolationException exception) {
            return persistenceService
                    .findByIdempotencyKey(userId, key)
                    .map(bookingMapper::toResponse)
                    .orElseThrow(() -> exception);
        }

        try {
            hotelServiceClient.confirmAvailability(roomId, booking.getId(), request.startDate(), request.endDate());
        } catch (RoomUnavailableException exception) {
            persistenceService.markCancelled(booking.getId(), REASON_ROOM_UNAVAILABLE);
            throw new ConflictException(exception.getMessage());
        } catch (HotelServiceUnavailableException exception) {
            persistenceService.markCancelled(booking.getId(), REASON_HOTEL_UNAVAILABLE);
            throw exception;
        } catch (HotelServiceErrorException exception) {
            persistenceService.markCancelled(booking.getId(), REASON_HOTEL_ERROR);
            throw exception;
        }

        try (MDC.MDCCloseable ignored =
                MDC.putCloseable("bookingId", booking.getId().toString())) {
            BookingResponse response = bookingMapper.toResponse(persistenceService.markConfirmed(booking.getId()));
            log.info("Booking confirmed for user {} in hotel {} room {}", userId, request.hotelId(), roomId);
            return response;
        } catch (RuntimeException exception) {
            compensateRelease(roomId, booking.getId(), request.startDate(), request.endDate());
            throw exception;
        }
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public PageResponse<BookingResponse> list(CurrentUser user, Pageable pageable) {
        Page<Booking> bookings = user.admin()
                ? persistenceService.pageAll(pageable)
                : persistenceService.pageForUser(requireUserId(user), pageable);

        return PageResponse.of(bookings.map(bookingMapper::toResponse));
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public BookingResponse get(CurrentUser user, Long bookingId) {
        Booking booking = persistenceService.get(bookingId);
        ensureAccess(user, booking);
        return bookingMapper.toResponse(booking);
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public BookingResponse cancel(CurrentUser user, Long bookingId) {
        Booking booking = persistenceService.get(bookingId);
        ensureAccess(user, booking);
        return cancelBooking(booking);
    }

    public BookingResponse cancelInternal(Long bookingId) {
        return cancelBooking(persistenceService.get(bookingId));
    }

    public BookingResponse getInternal(Long bookingId) {
        return bookingMapper.toResponse(persistenceService.get(bookingId));
    }

    private BookingResponse cancelBooking(Booking booking) {
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return bookingMapper.toResponse(booking);
        }

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            hotelServiceClient.release(
                    booking.getRoomId(), booking.getId(), booking.getStartDate(), booking.getEndDate());
        }

        BookingResponse response = bookingMapper.toResponse(persistenceService.markCancelled(booking.getId(), null));
        try (MDC.MDCCloseable ignored =
                MDC.putCloseable("bookingId", booking.getId().toString())) {
            log.info("Booking cancelled");
        }
        return response;
    }

    private Long selectRoom(CurrentUser user, String token, CreateBookingRequest request) {
        if (request.roomId() != null) {
            return request.roomId();
        }

        List<HotelRoom> rooms = hotelServiceClient.recommend(
                request.hotelId(), request.startDate(), request.endDate(), "Bearer " + token);
        if (rooms.isEmpty()) {
            throw new ConflictException("No available rooms for the requested dates");
        }
        return rooms.get(0).id();
    }

    private void ensureAccess(CurrentUser user, Booking booking) {
        if (!user.admin() && !booking.getUserId().equals(requireUserId(user))) {
            throw new ForbiddenException("Booking " + booking.getId() + " belongs to another user");
        }
    }

    private Long requireUserId(CurrentUser user) {
        if (user.id() == null) {
            throw new UnauthorizedException("Authenticated user is missing");
        }
        return user.id();
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String key = idempotencyKey.trim();
        if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BadRequestException(
                    "Idempotency-Key must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        return key;
    }

    private void validateRange(LocalDate startDate, LocalDate endDate) {
        if (!startDate.isBefore(endDate)) {
            throw new BadRequestException("startDate must be before endDate");
        }
    }

    private void compensateRelease(Long roomId, Long bookingId, LocalDate startDate, LocalDate endDate) {
        try {
            hotelServiceClient.release(roomId, bookingId, startDate, endDate);
        } catch (RuntimeException exception) {
            log.error("Failed to compensate room {} for booking {}", roomId, bookingId, exception);
        }
    }
}
