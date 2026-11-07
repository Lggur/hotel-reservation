package com.example.hotelreservation.hotel.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.hotelreservation.hotel.security.TestJwt;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "security.jwt.secret=" + TestJwt.SECRET,
        "spring.datasource.url=jdbc:h2:mem:availability-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@AutoConfigureMockMvc
@Transactional
class RoomAvailabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void confirmAvailabilityCreatesActiveSlotAndIncrementsTimesBooked() throws Exception {
        long roomId = createRoom(createHotel("Hotel"), "101");

        confirm(roomId, 1001L, "2026-01-10", "2026-01-12")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(roomId))
                .andExpect(jsonPath("$.bookingId").value(1001))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.timesBooked").value(1));

        mockMvc.perform(get("/api/rooms/{id}", roomId)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timesBooked").value(1));
    }

    @Test
    void confirmAvailabilityIsIdempotent() throws Exception {
        long roomId = createRoom(createHotel("Hotel"), "101");

        confirm(roomId, 1002L, "2026-01-10", "2026-01-12").andExpect(status().isOk());
        confirm(roomId, 1002L, "2026-01-10", "2026-01-12")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.timesBooked").value(1));

        mockMvc.perform(get("/api/rooms/statistics")
                        .param("from", "2026-01-01")
                        .param("to", "2026-02-01")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rooms[0].activeReservations").value(1))
                .andExpect(jsonPath("$.totalTimesBooked").value(1));
    }

    @Test
    void confirmSameBookingWithDifferentDatesIsConflict() throws Exception {
        long roomId = createRoom(createHotel("Hotel"), "101");

        confirm(roomId, 1003L, "2026-01-10", "2026-01-12").andExpect(status().isOk());
        confirm(roomId, 1003L, "2026-01-20", "2026-01-22")
                .andExpect(status().isConflict());
    }

    @Test
    void confirmSameBookingForAnotherRoomIsConflict() throws Exception {
        long hotelId = createHotel("Hotel");
        long firstRoomId = createRoom(hotelId, "101");
        long secondRoomId = createRoom(hotelId, "102");

        confirm(firstRoomId, 1004L, "2026-01-10", "2026-01-12").andExpect(status().isOk());
        confirm(secondRoomId, 1004L, "2026-01-10", "2026-01-12")
                .andExpect(status().isConflict());
    }

    @Test
    void confirmOverlappingDatesIsConflict() throws Exception {
        long roomId = createRoom(createHotel("Hotel"), "101");

        confirm(roomId, 1005L, "2026-01-10", "2026-01-15").andExpect(status().isOk());
        confirm(roomId, 1006L, "2026-01-14", "2026-01-16")
                .andExpect(status().isConflict());
    }

    @Test
    void confirmAdjacentDatesIsAllowed() throws Exception {
        long roomId = createRoom(createHotel("Hotel"), "101");

        confirm(roomId, 1007L, "2026-01-10", "2026-01-12").andExpect(status().isOk());
        confirm(roomId, 1008L, "2026-01-12", "2026-01-14")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timesBooked").value(2));
    }

    @Test
    void confirmUnavailableRoomIsConflict() throws Exception {
        long roomId = createRoom(createHotel("Hotel"), "101", false);

        confirm(roomId, 1009L, "2026-01-10", "2026-01-12")
                .andExpect(status().isConflict());
    }

    @Test
    void confirmMissingRoomIsNotFound() throws Exception {
        confirm(999_999L, 1010L, "2026-01-10", "2026-01-12")
                .andExpect(status().isNotFound());
    }

    @Test
    void confirmWithInvalidRangeIsBadRequest() throws Exception {
        long roomId = createRoom(createHotel("Hotel"), "101");

        confirm(roomId, 1011L, "2026-01-12", "2026-01-10")
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/rooms/{id}/confirm-availability", roomId)
                        .header(TestJwt.INTERNAL_TOKEN_HEADER, TestJwt.INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookingId":1012}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.startDate").exists())
                .andExpect(jsonPath("$.errors.endDate").exists());
    }

    @Test
    void releaseMakesRoomAvailableAndDecrementsTimesBooked() throws Exception {
        long roomId = createRoom(createHotel("Hotel"), "101");
        confirm(roomId, 2001L, "2026-03-01", "2026-03-05").andExpect(status().isOk());

        release(roomId, 2001L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"))
                .andExpect(jsonPath("$.timesBooked").value(0));

        mockMvc.perform(get("/api/rooms")
                        .param("startDate", "2026-03-01")
                        .param("endDate", "2026-03-05")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(roomId));
    }

    @Test
    void releaseIsIdempotent() throws Exception {
        long roomId = createRoom(createHotel("Hotel"), "101");
        confirm(roomId, 2002L, "2026-03-01", "2026-03-05").andExpect(status().isOk());

        release(roomId, 2002L).andExpect(status().isOk());
        release(roomId, 2002L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timesBooked").value(0));
    }

    @Test
    void releaseUnknownBookingCreatesTombstone() throws Exception {
        long roomId = createRoom(createHotel("Hotel"), "101");

        release(roomId, 2003L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));

        confirm(roomId, 2003L, "2026-03-01", "2026-03-05")
                .andExpect(status().isConflict());
    }

    @Test
    void releaseBookingOfAnotherRoomIsConflict() throws Exception {
        long hotelId = createHotel("Hotel");
        long firstRoomId = createRoom(hotelId, "101");
        long secondRoomId = createRoom(hotelId, "102");
        confirm(firstRoomId, 2004L, "2026-03-01", "2026-03-05").andExpect(status().isOk());

        release(secondRoomId, 2004L)
                .andExpect(status().isConflict());
    }

    @Test
    void internalEndpointRequiresInternalToken() throws Exception {
        long roomId = createRoom(createHotel("Hotel"), "101");

        mockMvc.perform(post("/api/rooms/{id}/confirm-availability", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookingId":3001,"startDate":"2026-04-01","endDate":"2026-04-02"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/rooms/{id}/confirm-availability", roomId)
                        .header(TestJwt.INTERNAL_TOKEN_HEADER, "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookingId":3001,"startDate":"2026-04-01","endDate":"2026-04-02"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/rooms/{id}/confirm-availability", roomId)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookingId":3001,"startDate":"2026-04-01","endDate":"2026-04-02"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/rooms/{id}/release", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookingId":3001}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void listRoomsWithoutDatesReturnsOnlyAvailableRooms() throws Exception {
        long hotelId = createHotel("Hotel");
        long availableRoomId = createRoom(hotelId, "101", true);
        createRoom(hotelId, "102", false);

        mockMvc.perform(get("/api/rooms")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(availableRoomId));
    }

    @Test
    void listRoomsWithDatesExcludesBookedRooms() throws Exception {
        long hotelId = createHotel("Hotel");
        long bookedRoomId = createRoom(hotelId, "101");
        long freeRoomId = createRoom(hotelId, "102");
        confirm(bookedRoomId, 4001L, "2026-08-01", "2026-08-03").andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms")
                        .param("startDate", "2026-08-02")
                        .param("endDate", "2026-08-04")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(freeRoomId));
    }

    @Test
    void listRoomsWithPartialDatesIsBadRequest() throws Exception {
        createRoom(createHotel("Hotel"), "101");

        mockMvc.perform(get("/api/rooms")
                        .param("startDate", "2026-08-02")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recommendOrdersAvailableRoomsByTimesBooked() throws Exception {
        long hotelId = createHotel("Hotel");
        long busyRoomId = createRoom(hotelId, "101");
        long freeRoomId = createRoom(hotelId, "102");
        confirm(busyRoomId, 5001L, "2026-05-01", "2026-05-03").andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms/recommend")
                        .param("startDate", "2026-06-01")
                        .param("endDate", "2026-06-03")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(freeRoomId))
                .andExpect(jsonPath("$[1].id").value(busyRoomId));
    }

    @Test
    void recommendExcludesBookedRooms() throws Exception {
        long hotelId = createHotel("Hotel");
        long bookedRoomId = createRoom(hotelId, "101");
        long freeRoomId = createRoom(hotelId, "102");
        confirm(bookedRoomId, 5002L, "2026-05-01", "2026-05-03").andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms/recommend")
                        .param("startDate", "2026-05-02")
                        .param("endDate", "2026-05-04")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(freeRoomId));
    }

    @Test
    void statisticsReturnsOccupancyPerRoom() throws Exception {
        long hotelId = createHotel("Hotel");
        long bookedRoomId = createRoom(hotelId, "101");
        long freeRoomId = createRoom(hotelId, "102");
        confirm(bookedRoomId, 6001L, "2026-02-01", "2026-02-05").andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms/statistics")
                        .param("from", "2026-02-01")
                        .param("to", "2026-02-11")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-02-01"))
                .andExpect(jsonPath("$.to").value("2026-02-11"))
                .andExpect(jsonPath("$.totalRooms").value(2))
                .andExpect(jsonPath("$.totalTimesBooked").value(1))
                .andExpect(jsonPath("$.rooms[0].roomId").value(bookedRoomId))
                .andExpect(jsonPath("$.rooms[0].activeReservations").value(1))
                .andExpect(jsonPath("$.rooms[0].occupiedNights").value(4))
                .andExpect(jsonPath("$.rooms[0].occupancyRate").value(0.4))
                .andExpect(jsonPath("$.rooms[1].roomId").value(freeRoomId))
                .andExpect(jsonPath("$.rooms[1].activeReservations").value(0))
                .andExpect(jsonPath("$.rooms[1].occupiedNights").value(0))
                .andExpect(jsonPath("$.rooms[1].occupancyRate").value(0.0));
    }

    @Test
    void statisticsClipsReservationsToRequestedPeriod() throws Exception {
        long hotelId = createHotel("Hotel");
        long roomId = createRoom(hotelId, "101");
        confirm(roomId, 6002L, "2026-02-01", "2026-02-10").andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms/statistics")
                        .param("from", "2026-02-03")
                        .param("to", "2026-02-05")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rooms[0].occupiedNights").value(2))
                .andExpect(jsonPath("$.rooms[0].occupancyRate").value(1.0));
    }

    @Test
    void statisticsAsUserIsForbidden() throws Exception {
        mockMvc.perform(get("/api/rooms/statistics")
                        .param("from", "2026-02-01")
                        .param("to", "2026-02-11")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user())))
                .andExpect(status().isForbidden());
    }

    @Test
    void statisticsWithMissingOrInvalidPeriodIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/rooms/statistics")
                        .param("from", "2026-02-01")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.admin())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/rooms/statistics")
                        .param("from", "2026-02-11")
                        .param("to", "2026-02-01")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.admin())))
                .andExpect(status().isBadRequest());
    }

    private ResultActions confirm(long roomId, long bookingId, String startDate, String endDate) throws Exception {
        return mockMvc.perform(post("/api/rooms/{id}/confirm-availability", roomId)
                .header(TestJwt.INTERNAL_TOKEN_HEADER, TestJwt.INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bookingId":%d,"requestId":"req-%d","startDate":"%s","endDate":"%s"}
                        """.formatted(bookingId, bookingId, startDate, endDate)));
    }

    private ResultActions release(long roomId, long bookingId) throws Exception {
        return mockMvc.perform(post("/api/rooms/{id}/release", roomId)
                .header(TestJwt.INTERNAL_TOKEN_HEADER, TestJwt.INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bookingId":%d,"requestId":"rel-%d"}
                        """.formatted(bookingId, bookingId)));
    }

    private long createHotel(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/hotels")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","address":"Address"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createRoom(long hotelId, String number) throws Exception {
        return createRoom(hotelId, number, true);
    }

    private long createRoom(long hotelId, String number, boolean available) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hotelId":%d,"number":"%s","available":%s}
                                """.formatted(hotelId, number, available)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
