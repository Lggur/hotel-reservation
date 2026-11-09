package com.example.hotelreservation.booking.booking;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.hotelreservation.booking.booking.entity.Booking;
import com.example.hotelreservation.booking.booking.entity.BookingStatus;
import com.example.hotelreservation.booking.booking.repository.BookingRepository;
import com.example.hotelreservation.booking.security.TestJwt;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookingFlowIntegrationTest {

    private static final LocalDate START = LocalDate.parse("2026-01-10");
    private static final LocalDate END = LocalDate.parse("2026-01-12");
    private static final WireMockServer HOTEL_SERVICE =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        HOTEL_SERVICE.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("hotel-service.base-url", HOTEL_SERVICE::baseUrl);
        registry.add("eureka.client.enabled", () -> false);
        registry.add("security.jwt.secret", () -> TestJwt.SECRET);
        registry.add("booking.recovery.enabled", () -> false);
        registry.add("hotel-service.retry.backoff", () -> "10ms");
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:h2:mem:booking-flow-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
    }

    @AfterAll
    static void stopHotelService() {
        HOTEL_SERVICE.stop();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingRepository bookingRepository;

    @BeforeEach
    void resetStubs() {
        HOTEL_SERVICE.resetAll();
    }

    @Test
    void createAndCancelBookingCallsHotelService() throws Exception {
        String token = TestJwt.user(1);
        HOTEL_SERVICE.stubFor(
                post(urlEqualTo("/api/rooms/10/confirm-availability")).willReturn(ok()));

        MvcResult created = mockMvc.perform(MockMvcRequestBuilders.post("/api/bookings")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(5L, 10L)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn();

        long bookingId = objectMapper
                .readTree(created.getResponse().getContentAsString())
                .get("id")
                .asLong();

        HOTEL_SERVICE.verify(postRequestedFor(urlEqualTo("/api/rooms/10/confirm-availability"))
                .withHeader("X-Internal-Token", equalTo(TestJwt.INTERNAL_TOKEN))
                .withRequestBody(matchingJsonPath("$.bookingId", equalTo(String.valueOf(bookingId)))));

        HOTEL_SERVICE.stubFor(post(urlEqualTo("/api/rooms/10/release")).willReturn(ok()));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/bookings/{id}/cancel", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        HOTEL_SERVICE.verify(postRequestedFor(urlEqualTo("/api/rooms/10/release"))
                .withHeader("X-Internal-Token", equalTo(TestJwt.INTERNAL_TOKEN))
                .withRequestBody(matchingJsonPath("$.bookingId", equalTo(String.valueOf(bookingId)))));
    }

    @Test
    void autoSelectsRoomUsingUserToken() throws Exception {
        String token = TestJwt.user(1);
        HOTEL_SERVICE.stubFor(
                get(urlPathEqualTo("/api/rooms/recommend"))
                        .withHeader("Authorization", equalTo("Bearer " + token))
                        .withQueryParam("hotelId", equalTo("7"))
                        .willReturn(
                                okJson(
                                        """
                        [{"id":12,"hotelId":7,"number":"202","available":true,"timesBooked":0}]
                        """)));
        HOTEL_SERVICE.stubFor(
                post(urlEqualTo("/api/rooms/12/confirm-availability")).willReturn(ok()));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/bookings")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(7L, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(12))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void hotelConflictMarksBookingCancelled() throws Exception {
        HOTEL_SERVICE.stubFor(post(urlEqualTo("/api/rooms/10/confirm-availability"))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"title\":\"Data conflict\",\"status\":409}")));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/bookings")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user(1)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(5L, 10L)))
                .andExpect(status().isConflict());

        Booking booking = bookingRepository.findAll().get(0);
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals("ROOM_UNAVAILABLE", booking.getFailureReason());
    }

    @Test
    void hotelServerErrorMarksBookingCancelled() throws Exception {
        HOTEL_SERVICE.stubFor(post(urlEqualTo("/api/rooms/10/confirm-availability"))
                .willReturn(aResponse().withStatus(500)));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/bookings")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(TestJwt.user(1)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(5L, 10L)))
                .andExpect(status().isServiceUnavailable());

        Booking booking = bookingRepository.findAll().get(0);
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals("HOTEL_SERVICE_UNAVAILABLE", booking.getFailureReason());
    }

    private String bookingJson(long hotelId, Long roomId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("hotelId", hotelId);
        if (roomId != null) {
            body.put("roomId", roomId);
        }
        body.put("startDate", START.toString());
        body.put("endDate", END.toString());
        return objectMapper.writeValueAsString(body);
    }
}
