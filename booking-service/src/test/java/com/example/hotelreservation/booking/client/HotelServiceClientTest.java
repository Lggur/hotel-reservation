package com.example.hotelreservation.booking.client;

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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.hotelreservation.booking.client.dto.HotelRoom;
import com.example.hotelreservation.booking.exception.HotelServiceUnavailableException;
import com.example.hotelreservation.booking.exception.RoomUnavailableException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class HotelServiceClientTest {

    private static final LocalDate START = LocalDate.parse("2026-01-10");
    private static final LocalDate END = LocalDate.parse("2026-01-12");

    private WireMockServer server;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void recommendSendsAuthorizationAndMapsRooms() {
        server.stubFor(get(urlPathEqualTo("/api/rooms/recommend"))
                .withQueryParam("hotelId", equalTo("7"))
                .withQueryParam("startDate", equalTo("2026-01-10"))
                .withQueryParam("endDate", equalTo("2026-01-12"))
                .withHeader("Authorization", equalTo("Bearer user-token"))
                .willReturn(okJson("""
                        [{"id":11,"hotelId":7,"number":"101","available":true,"timesBooked":2}]
                        """)));

        List<HotelRoom> rooms = client(Duration.ofSeconds(2))
                .recommend(7L, START, END, "Bearer user-token");

        assertEquals(1, rooms.size());
        assertEquals(11L, rooms.get(0).id());
        assertEquals(7L, rooms.get(0).hotelId());
    }

    @Test
    void confirmSendsInternalTokenAndSlotPayload() {
        server.stubFor(post(urlEqualTo("/api/rooms/10/confirm-availability"))
                .willReturn(ok()));

        client(Duration.ofSeconds(2)).confirmAvailability(10L, 100L, START, END);

        server.verify(postRequestedFor(urlEqualTo("/api/rooms/10/confirm-availability"))
                .withHeader("X-Internal-Token", equalTo("internal-token"))
                .withRequestBody(matchingJsonPath("$.bookingId", equalTo("100")))
                .withRequestBody(matchingJsonPath("$.startDate", equalTo("2026-01-10")))
                .withRequestBody(matchingJsonPath("$.endDate", equalTo("2026-01-12"))));
    }

    @Test
    void confirmConflictMeansRoomIsUnavailable() {
        server.stubFor(post(urlEqualTo("/api/rooms/10/confirm-availability"))
                .willReturn(aResponse().withStatus(409)));

        assertThrows(RoomUnavailableException.class,
                () -> client(Duration.ofSeconds(2)).confirmAvailability(10L, 100L, START, END));
    }

    @Test
    void confirmServerErrorMeansHotelServiceUnavailable() {
        server.stubFor(post(urlEqualTo("/api/rooms/10/confirm-availability"))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(HotelServiceUnavailableException.class,
                () -> client(Duration.ofSeconds(2)).confirmAvailability(10L, 100L, START, END));
    }

    @Test
    void confirmTimeoutMeansHotelServiceUnavailable() {
        server.stubFor(post(urlEqualTo("/api/rooms/10/confirm-availability"))
                .willReturn(ok().withFixedDelay(1500)));

        assertThrows(HotelServiceUnavailableException.class,
                () -> client(Duration.ofMillis(300)).confirmAvailability(10L, 100L, START, END));
    }

    @Test
    void releaseSendsInternalTokenAndSlotPayload() {
        server.stubFor(post(urlEqualTo("/api/rooms/10/release"))
                .willReturn(ok()));

        client(Duration.ofSeconds(2)).release(10L, 100L, START, END);

        server.verify(postRequestedFor(urlEqualTo("/api/rooms/10/release"))
                .withHeader("X-Internal-Token", equalTo("internal-token"))
                .withRequestBody(matchingJsonPath("$.bookingId", equalTo("100"))));
    }

    @Test
    void releaseServerErrorMeansHotelServiceUnavailable() {
        server.stubFor(post(urlEqualTo("/api/rooms/10/release"))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(HotelServiceUnavailableException.class,
                () -> client(Duration.ofSeconds(2)).release(10L, 100L, START, END));
    }

    private HotelServiceClient client(Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        requestFactory.setReadTimeout(readTimeout);

        RestClient restClient = RestClient.builder()
                .baseUrl(server.baseUrl())
                .requestFactory(requestFactory)
                .build();

        return new HotelServiceClient(restClient, "internal-token");
    }
}
