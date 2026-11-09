package com.example.hotelreservation.booking.booking.service;

import com.example.hotelreservation.booking.booking.entity.Booking;
import com.example.hotelreservation.booking.client.HotelServiceClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class StaleBookingRecoveryService {

    static final String REASON = "STALE_PENDING_RECOVERED";

    private final BookingPersistenceService persistenceService;
    private final HotelServiceClient hotelServiceClient;
    private final Duration pendingTimeout;

    public StaleBookingRecoveryService(
            BookingPersistenceService persistenceService,
            HotelServiceClient hotelServiceClient,
            @Value("${booking.recovery.pending-timeout:PT10M}") Duration pendingTimeout) {
        this.persistenceService = persistenceService;
        this.hotelServiceClient = hotelServiceClient;
        this.pendingTimeout = pendingTimeout;
    }

    public int recoverStaleBookings() {
        List<Booking> staleBookings = persistenceService.findStalePending(pendingTimeout);
        int recovered = 0;
        for (Booking booking : staleBookings) {
            if (recover(booking)) {
                recovered++;
            }
        }
        return recovered;
    }

    private boolean recover(Booking booking) {
        try {
            hotelServiceClient.release(
                    booking.getRoomId(), booking.getId(), booking.getStartDate(), booking.getEndDate());
        } catch (RuntimeException exception) {
            log.warn(
                    "Could not release room {} for stale pending booking {}; leaving it PENDING",
                    booking.getRoomId(),
                    booking.getId(),
                    exception);
            return false;
        }

        try {
            Optional<Booking> cancelled = persistenceService.markCancelledIfPending(booking.getId(), REASON);
            if (cancelled.isEmpty()) {
                log.info("Booking {} is no longer PENDING after its room was released", booking.getId());
                return false;
            }
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to cancel stale pending booking {} after releasing room {}",
                    booking.getId(),
                    booking.getRoomId(),
                    exception);
            return false;
        }

        log.info("Recovered stale pending booking {}", booking.getId());
        return true;
    }
}
