package com.example.hotelreservation.booking.booking.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.hotelreservation.booking.booking.dto.CreateBookingRequest;
import com.example.hotelreservation.booking.booking.entity.Booking;
import com.example.hotelreservation.booking.booking.entity.BookingStatus;
import com.example.hotelreservation.booking.booking.mapper.BookingMapper;
import com.example.hotelreservation.booking.client.HotelServiceClient;
import com.example.hotelreservation.booking.exception.HotelServiceUnavailableException;
import com.example.hotelreservation.booking.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookingServiceCompensationTest {

    private static final LocalDate START = LocalDate.parse("2026-01-10");
    private static final LocalDate END = LocalDate.parse("2026-01-12");

    private final HotelServiceClient hotelServiceClient = mock(HotelServiceClient.class);
    private final BookingPersistenceService persistenceService = mock(BookingPersistenceService.class);
    private final BookingMapper bookingMapper = mock(BookingMapper.class);
    private final BookingService bookingService =
            new BookingService(hotelServiceClient, persistenceService, bookingMapper);

    private final CurrentUser user = new CurrentUser(1L, "user", List.of("USER"));

    @Test
    void releasesRoomWhenLocalConfirmationFailsAfterHotelSuccess() {
        Booking pending = booking(1L, BookingStatus.PENDING);
        when(persistenceService.createPending(1L, 5L, 10L, START, END)).thenReturn(pending);
        when(persistenceService.markConfirmed(1L)).thenThrow(new IllegalStateException("Database failure"));

        assertThrows(IllegalStateException.class, () -> bookingService.create(
                user, "token", new CreateBookingRequest(5L, 10L, START, END)));

        verify(hotelServiceClient).release(10L, 1L, START, END);
    }

    @Test
    void keepsBookingConfirmedWhenHotelReleaseFails() {
        Booking confirmed = booking(2L, BookingStatus.CONFIRMED);
        when(persistenceService.get(2L)).thenReturn(confirmed);
        doThrow(new HotelServiceUnavailableException("Hotel service is unavailable"))
                .when(hotelServiceClient).release(10L, 2L, START, END);

        assertThrows(HotelServiceUnavailableException.class,
                () -> bookingService.cancel(user, 2L));

        verify(persistenceService, never()).markCancelled(anyLong(), any());
    }

    @Test
    void cancelsPendingBookingWithoutCallingHotel() {
        Booking pending = booking(3L, BookingStatus.PENDING);
        when(persistenceService.get(3L)).thenReturn(pending);
        when(persistenceService.markCancelled(3L, null))
                .thenReturn(booking(3L, BookingStatus.CANCELLED));

        bookingService.cancel(user, 3L);

        verifyNoInteractions(hotelServiceClient);
        verify(persistenceService).markCancelled(3L, null);
    }

    private Booking booking(long id, BookingStatus status) {
        Booking booking = new Booking();
        booking.setId(id);
        booking.setUserId(1L);
        booking.setHotelId(5L);
        booking.setRoomId(10L);
        booking.setStartDate(START);
        booking.setEndDate(END);
        booking.setStatus(status);
        return booking;
    }
}
