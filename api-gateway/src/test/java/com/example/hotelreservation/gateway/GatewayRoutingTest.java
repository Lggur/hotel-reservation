package com.example.hotelreservation.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

    private static final WireMockServer DOWNSTREAM =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        DOWNSTREAM.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("eureka.client.enabled", () -> false);
        registry.add("spring.cloud.discovery.client.simple.instances.booking-service[0].uri",
                DOWNSTREAM::baseUrl);
        registry.add("spring.cloud.discovery.client.simple.instances.hotel-service[0].uri",
                DOWNSTREAM::baseUrl);
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.stop();
    }

    @LocalServerPort
    private int port;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        DOWNSTREAM.resetAll();
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void routesAuthRequestsToBookingService() {
        DOWNSTREAM.stubFor(post(urlEqualTo("/api/auth/login"))
                .willReturn(okJson("{\"token\":\"jwt-token\"}")));

        client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"alice\",\"password\":\"secret123\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isEqualTo("jwt-token");

        DOWNSTREAM.verify(postRequestedFor(urlEqualTo("/api/auth/login"))
                .withRequestBody(matchingJsonPath("$.username", equalTo("alice"))));
    }

    @Test
    void routesBookingRequestsAndForwardsAuthorizationHeader() {
        DOWNSTREAM.stubFor(get(urlEqualTo("/api/bookings"))
                .willReturn(okJson("[]")));

        client.get().uri("/api/bookings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer some-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(0);

        DOWNSTREAM.verify(getRequestedFor(urlEqualTo("/api/bookings"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer some-token")));
    }

    @Test
    void routesHotelAndRoomRequestsToHotelService() {
        DOWNSTREAM.stubFor(get(urlEqualTo("/api/hotels/5"))
                .willReturn(okJson("{\"id\":5,\"name\":\"Hilton\"}")));
        DOWNSTREAM.stubFor(get(urlPathEqualTo("/api/rooms/recommend"))
                .withQueryParam("startDate", equalTo("2026-01-10"))
                .withQueryParam("endDate", equalTo("2026-01-12"))
                .willReturn(okJson("[{\"id\":10}]")));

        client.get().uri("/api/hotels/5")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Hilton");

        client.get().uri("/api/rooms/recommend?startDate=2026-01-10&endDate=2026-01-12")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(10);
    }

    @Test
    void doesNotRouteInternalEndpoints() {
        client.get().uri("/internal/bookings/1")
                .exchange()
                .expectStatus().isNotFound();

        client.post().uri("/api/rooms/10/confirm-availability")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isNotFound();

        client.post().uri("/api/rooms/10/release")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isNotFound();

        assertEquals(0, DOWNSTREAM.getAllServeEvents().size());
    }

    @Test
    void doesNotExposeUnknownPaths() {
        client.get().uri("/swagger-ui/index.html")
                .exchange()
                .expectStatus().isNotFound();

        assertEquals(0, DOWNSTREAM.getAllServeEvents().size());
    }
}
