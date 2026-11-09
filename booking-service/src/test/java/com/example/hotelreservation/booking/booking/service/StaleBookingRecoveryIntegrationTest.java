package com.example.hotelreservation.booking.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.hotelreservation.booking.booking.entity.Booking;
import com.example.hotelreservation.booking.booking.entity.BookingStatus;
import com.example.hotelreservation.booking.booking.repository.BookingRepository;
import com.example.hotelreservation.booking.client.HotelServiceClient;
import com.example.hotelreservation.booking.exception.HotelServiceUnavailableException;
import com.example.hotelreservation.booking.security.TestJwt;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        properties = {
            "eureka.client.enabled=false",
            "booking.recovery.enabled=false",
            "security.jwt.secret=" + TestJwt.SECRET,
            "spring.datasource.url=jdbc:h2:mem:booking-recovery-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
        })
@Transactional
class StaleBookingRecoveryIntegrationTest {

    private static final LocalDate START = LocalDate.parse("2026-01-10");
    private static final LocalDate END = LocalDate.parse("2026-01-12");

    @Autowired
    private StaleBookingRecoveryService recoveryService;

    @Autowired
    private BookingPersistenceService persistenceService;

    @Autowired
    private BookingRepository bookingRepository;

    @MockitoBean
    private HotelServiceClient hotelServiceClient;

    @Test
    void releasesRoomAndCancelsStalePendingBooking() {
        Booking stale = stalePending(10L);

        int recovered = recoveryService.recoverStaleBookings();

        assertEquals(1, recovered);
        verify(hotelServiceClient).release(10L, stale.getId(), START, END);

        Booking booking = bookingRepository.findById(stale.getId()).orElseThrow();
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals("STALE_PENDING_RECOVERED", booking.getFailureReason());
    }

    @Test
    void leavesFreshPendingBookingUntouched() {
        Booking fresh = persistenceService.createPending(1L, 5L, 10L, START, END);

        assertEquals(0, recoveryService.recoverStaleBookings());
        verifyNoInteractions(hotelServiceClient);
        assertEquals(BookingStatus.PENDING, reload(fresh).getStatus());
    }

    @Test
    void leavesConfirmedBookingUntouched() {
        Booking pending = stalePending(10L);
        persistenceService.markConfirmed(pending.getId());

        assertEquals(0, recoveryService.recoverStaleBookings());
        verifyNoInteractions(hotelServiceClient);
        assertEquals(BookingStatus.CONFIRMED, reload(pending).getStatus());
    }

    @Test
    void leavesPendingBookingWhenReleaseFails() {
        Booking stale = stalePending(10L);
        doThrow(new HotelServiceUnavailableException("Hotel service is unavailable"))
                .when(hotelServiceClient)
                .release(anyLong(), anyLong(), any(), any());

        assertEquals(0, recoveryService.recoverStaleBookings());

        Booking booking = reload(stale);
        assertEquals(BookingStatus.PENDING, booking.getStatus());
        assertNull(booking.getFailureReason());
    }

    private Booking stalePending(long roomId) {
        Booking booking = persistenceService.createPending(1L, 5L, roomId, START, END);
        booking.setCreatedAt(Instant.now().minus(Duration.ofMinutes(15)));
        return bookingRepository.saveAndFlush(booking);
    }

    private Booking reload(Booking booking) {
        return bookingRepository.findById(booking.getId()).orElseThrow();
    }
}
