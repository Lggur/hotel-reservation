package com.example.hotelreservation.booking.booking.schedule;

import com.example.hotelreservation.booking.booking.service.StaleBookingRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "booking.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class StaleBookingRecoveryJob {

    private final StaleBookingRecoveryService recoveryService;

    @Scheduled(fixedDelayString = "${booking.recovery.interval-ms:60000}")
    public void recoverStaleBookings() {
        try {
            int recovered = recoveryService.recoverStaleBookings();
            if (recovered > 0) {
                log.info("Recovered {} stale pending bookings", recovered);
            }
        } catch (RuntimeException exception) {
            log.error("Stale pending booking recovery failed", exception);
        }
    }
}
