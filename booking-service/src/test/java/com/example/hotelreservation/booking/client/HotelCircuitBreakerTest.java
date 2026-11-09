package com.example.hotelreservation.booking.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.hotelreservation.booking.exception.HotelServiceUnavailableException;
import com.example.hotelreservation.booking.security.TestJwt;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
        properties = {
            "eureka.client.enabled=false",
            "security.jwt.secret=" + TestJwt.SECRET,
            "booking.recovery.enabled=false",
            "hotel-service.retry.max-attempts=1",
            "hotel-service.retry.backoff=1ms",
            "resilience4j.circuitbreaker.instances.hotelService.minimum-number-of-calls=2",
            "resilience4j.circuitbreaker.instances.hotelService.sliding-window-size=2",
            "resilience4j.circuitbreaker.instances.hotelService.failure-rate-threshold=50",
            "spring.datasource.url=jdbc:h2:mem:circuit-breaker-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
        })
class HotelCircuitBreakerTest {

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
    }

    @AfterAll
    static void stopHotelService() {
        HOTEL_SERVICE.stop();
    }

    @Autowired
    private HotelServiceClient hotelServiceClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        HOTEL_SERVICE.resetAll();
        circuitBreakerRegistry.circuitBreaker("hotelService").reset();
    }

    @Test
    void opensCircuitAndStopsCallingHotelService() {
        HOTEL_SERVICE.stubFor(post(urlEqualTo("/api/rooms/10/confirm-availability"))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(
                HotelServiceUnavailableException.class,
                () -> hotelServiceClient.confirmAvailability(10L, 100L, START, END));
        assertThrows(
                HotelServiceUnavailableException.class,
                () -> hotelServiceClient.confirmAvailability(10L, 100L, START, END));

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("hotelService");
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertEquals(
                2,
                HOTEL_SERVICE
                        .findAll(postRequestedFor(urlEqualTo("/api/rooms/10/confirm-availability")))
                        .size());

        assertThrows(
                HotelServiceUnavailableException.class,
                () -> hotelServiceClient.confirmAvailability(10L, 100L, START, END));
        assertEquals(
                2,
                HOTEL_SERVICE
                        .findAll(postRequestedFor(urlEqualTo("/api/rooms/10/confirm-availability")))
                        .size());
    }
}
