package com.example.hotelreservation.booking.booking.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.hotelreservation.booking.booking.entity.Booking;
import com.example.hotelreservation.booking.booking.entity.BookingStatus;
import com.example.hotelreservation.booking.booking.repository.BookingRepository;
import com.example.hotelreservation.booking.booking.service.BookingPersistenceService;
import com.example.hotelreservation.booking.client.HotelServiceClient;
import com.example.hotelreservation.booking.client.dto.HotelRoom;
import com.example.hotelreservation.booking.exception.HotelServiceUnavailableException;
import com.example.hotelreservation.booking.exception.RoomUnavailableException;
import com.example.hotelreservation.booking.security.TestJwt;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "security.jwt.secret=" + TestJwt.SECRET,
        "spring.datasource.url=jdbc:h2:mem:booking-controller-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@AutoConfigureMockMvc
@Transactional
class BookingControllerTest {

    private static final LocalDate START = LocalDate.parse("2026-01-10");
    private static final LocalDate END = LocalDate.parse("2026-01-12");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingPersistenceService persistenceService;

    @MockitoBean
    private HotelServiceClient hotelServiceClient;

    @Test
    void createWithExplicitRoomConfirmsBooking() throws Exception {
        String token = TestJwt.user(1);

        createBooking(token, 5L, 10L, START, END)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.hotelId").value(5))
                .andExpect(jsonPath("$.roomId").value(10))
                .andExpect(jsonPath("$.startDate").value("2026-01-10"))
                .andExpect(jsonPath("$.endDate").value("2026-01-12"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        verify(hotelServiceClient).confirmAvailability(eq(10L), anyLong(), eq(START), eq(END));
        verify(hotelServiceClient, never()).recommend(any(), any(), any(), any());
    }

    @Test
    void createAutoSelectsRecommendedRoom() throws Exception {
        String token = TestJwt.user(1);
        when(hotelServiceClient.recommend(eq(7L), eq(START), eq(END), eq("Bearer " + token)))
                .thenReturn(List.of(new HotelRoom(21L, 7L, "101")));

        createBooking(token, 7L, null, START, END)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(21))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        verify(hotelServiceClient).confirmAvailability(eq(21L), anyLong(), eq(START), eq(END));
    }

    @Test
    void createWithoutAvailableRoomsIsConflict() throws Exception {
        String token = TestJwt.user(1);
        when(hotelServiceClient.recommend(eq(7L), eq(START), eq(END), any()))
                .thenReturn(List.of());

        createBooking(token, 7L, null, START, END)
                .andExpect(status().isConflict());

        assertEquals(0, bookingRepository.count());
        verify(hotelServiceClient, never()).confirmAvailability(anyLong(), anyLong(), any(), any());
    }

    @Test
    void createWhenHotelRejectsDatesIsConflictAndCancels() throws Exception {
        doThrow(new RoomUnavailableException("Room 10 is not available for the requested dates"))
                .when(hotelServiceClient).confirmAvailability(anyLong(), anyLong(), any(), any());

        createBooking(TestJwt.user(1), 5L, 10L, START, END)
                .andExpect(status().isConflict());

        Booking booking = onlyBooking();
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals("ROOM_UNAVAILABLE", booking.getFailureReason());
    }

    @Test
    void createWhenHotelIsUnavailableIsServiceUnavailableAndCancels() throws Exception {
        doThrow(new HotelServiceUnavailableException("Hotel service is unavailable"))
                .when(hotelServiceClient).confirmAvailability(anyLong(), anyLong(), any(), any());

        createBooking(TestJwt.user(1), 5L, 10L, START, END)
                .andExpect(status().isServiceUnavailable());

        Booking booking = onlyBooking();
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals("HOTEL_SERVICE_UNAVAILABLE", booking.getFailureReason());
    }

    @Test
    void getBookingChecksOwnership() throws Exception {
        long bookingId = createConfirmedBooking(TestJwt.user(1), 10L);

        mockMvc.perform(get("/api/bookings/{id}", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId));

        mockMvc.perform(get("/api/bookings/{id}", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user(2))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/bookings/{id}", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.admin())))
                .andExpect(status().isOk());
    }

    @Test
    void getMissingBookingIsNotFound() throws Exception {
        mockMvc.perform(get("/api/bookings/{id}", 999_999)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user(1))))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturnsOwnBookingsForUserAndAllForAdmin() throws Exception {
        createConfirmedBooking(TestJwt.user(1), 10L);
        createConfirmedBooking(TestJwt.user(2), 11L);

        mockMvc.perform(get("/api/bookings")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(1));

        mockMvc.perform(get("/api/bookings")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void cancelConfirmedBookingReleasesRoom() throws Exception {
        long bookingId = createConfirmedBooking(TestJwt.user(1), 10L);

        mockMvc.perform(post("/api/bookings/{id}/cancel", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(hotelServiceClient).release(10L, bookingId, START, END);
    }

    @Test
    void cancelIsIdempotent() throws Exception {
        long bookingId = createConfirmedBooking(TestJwt.user(1), 10L);

        mockMvc.perform(post("/api/bookings/{id}/cancel", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user(1))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/bookings/{id}/cancel", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(hotelServiceClient, times(1)).release(anyLong(), anyLong(), any(), any());
    }

    @Test
    void cancelViaDeleteMappingWorks() throws Exception {
        long bookingId = createConfirmedBooking(TestJwt.user(1), 10L);

        mockMvc.perform(delete("/api/bookings/{id}", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(hotelServiceClient).release(10L, bookingId, START, END);
    }

    @Test
    void cancelOtherUserBookingIsForbidden() throws Exception {
        long bookingId = createConfirmedBooking(TestJwt.user(1), 10L);

        mockMvc.perform(post("/api/bookings/{id}/cancel", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user(2))))
                .andExpect(status().isForbidden());

        verify(hotelServiceClient, never()).release(anyLong(), anyLong(), any(), any());
    }

    @Test
    void cancelKeepsBookingConfirmedWhenReleaseFails() throws Exception {
        long bookingId = createConfirmedBooking(TestJwt.user(1), 10L);
        doThrow(new HotelServiceUnavailableException("Hotel service is unavailable"))
                .when(hotelServiceClient).release(anyLong(), anyLong(), any(), any());

        mockMvc.perform(post("/api/bookings/{id}/cancel", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user(1))))
                .andExpect(status().isServiceUnavailable());

        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
    }

    @Test
    void cancelPendingBookingDoesNotCallHotel() throws Exception {
        Booking pending = persistenceService.createPending(1L, 5L, 10L, START, END);

        mockMvc.perform(post("/api/bookings/{id}/cancel", pending.getId())
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(hotelServiceClient, never()).release(anyLong(), anyLong(), any(), any());
    }

    @Test
    void internalEndpointsRequireInternalToken() throws Exception {
        long bookingId = createConfirmedBooking(TestJwt.user(1), 10L);

        mockMvc.perform(get("/internal/bookings/{id}", bookingId))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/internal/bookings/{id}", bookingId)
                        .header(TestJwt.INTERNAL_TOKEN_HEADER, "wrong-token"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/internal/bookings/{id}", bookingId)
                        .header(TestJwt.INTERNAL_TOKEN_HEADER, TestJwt.INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId));
    }

    @Test
    void internalCancelWorksWithoutJwt() throws Exception {
        long bookingId = createConfirmedBooking(TestJwt.user(1), 10L);

        mockMvc.perform(post("/internal/bookings/{id}/cancel", bookingId)
                        .header(TestJwt.INTERNAL_TOKEN_HEADER, TestJwt.INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(hotelServiceClient).release(10L, bookingId, START, END);
    }

    @Test
    void createWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(5L, 10L, START, END)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createWithInvalidRequestIsBadRequest() throws Exception {
        Map<String, Object> noHotel = new LinkedHashMap<>();
        noHotel.put("startDate", START.toString());
        noHotel.put("endDate", END.toString());

        mockMvc.perform(post("/api/bookings")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user(1)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noHotel)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.hotelId").exists());

        mockMvc.perform(post("/api/bookings")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user(1)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(5L, 0L, START, END)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.roomId").exists());
    }

    @Test
    void createWithInvalidDateRangeIsBadRequest() throws Exception {
        createBooking(TestJwt.user(1), 5L, 10L, END, START)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("startDate must be before endDate"));
    }

    private long createConfirmedBooking(String token, long roomId) throws Exception {
        MvcResult result = createBooking(token, 5L, roomId, START, END)
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private ResultActions createBooking(
            String token,
            long hotelId,
            Long roomId,
            LocalDate startDate,
            LocalDate endDate
    ) throws Exception {
        return mockMvc.perform(post("/api/bookings")
                .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson(hotelId, roomId, startDate, endDate)));
    }

    private String bookingJson(long hotelId, Long roomId, LocalDate startDate, LocalDate endDate)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("hotelId", hotelId);
        if (roomId != null) {
            body.put("roomId", roomId);
        }
        body.put("startDate", startDate.toString());
        body.put("endDate", endDate.toString());
        return objectMapper.writeValueAsString(body);
    }

    private Booking onlyBooking() {
        return bookingRepository.findAll().get(0);
    }
}
