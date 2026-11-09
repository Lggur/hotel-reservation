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
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

    private static final String SECRET = "hotel-reservation-dev-secret-key-please-change-2026";
    private static final JwtEncoder ENCODER = createEncoder();
    private static final WireMockServer DOWNSTREAM =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        DOWNSTREAM.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("eureka.client.enabled", () -> false);
        registry.add("spring.cloud.discovery.client.simple.instances.booking-service[0].uri", DOWNSTREAM::baseUrl);
        registry.add("spring.cloud.discovery.client.simple.instances.hotel-service[0].uri", DOWNSTREAM::baseUrl);
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
        client =
                WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void routesAuthRequestsToBookingService() {
        DOWNSTREAM.stubFor(post(urlEqualTo("/api/auth/login")).willReturn(okJson("{\"token\":\"jwt-token\"}")));

        client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"alice\",\"password\":\"secret123\"}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.token")
                .isEqualTo("jwt-token");

        DOWNSTREAM.verify(postRequestedFor(urlEqualTo("/api/auth/login"))
                .withRequestBody(matchingJsonPath("$.username", equalTo("alice"))));
    }

    @Test
    void routesBookingRequestsAndForwardsAuthorizationHeader() {
        String token = token();
        DOWNSTREAM.stubFor(get(urlEqualTo("/api/bookings")).willReturn(okJson("[]")));

        client.get()
                .uri("/api/bookings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(0);

        DOWNSTREAM.verify(getRequestedFor(urlEqualTo("/api/bookings"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + token)));
    }

    @Test
    void routesHotelAndRoomRequestsToHotelService() {
        DOWNSTREAM.stubFor(get(urlEqualTo("/api/hotels/5")).willReturn(okJson("{\"id\":5,\"name\":\"Hilton\"}")));
        DOWNSTREAM.stubFor(get(urlPathEqualTo("/api/rooms/recommend"))
                .withQueryParam("startDate", equalTo("2026-01-10"))
                .withQueryParam("endDate", equalTo("2026-01-12"))
                .willReturn(okJson("[{\"id\":10}]")));

        client.get()
                .uri("/api/hotels/5")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.name")
                .isEqualTo("Hilton");

        client.get()
                .uri("/api/rooms/recommend?startDate=2026-01-10&endDate=2026-01-12")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].id")
                .isEqualTo(10);
    }

    @Test
    void requiresJwtForProtectedRoutes() {
        client.get().uri("/api/bookings").exchange().expectStatus().isUnauthorized();

        client.get()
                .uri("/api/bookings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .exchange()
                .expectStatus()
                .isUnauthorized();

        assertEquals(0, DOWNSTREAM.getAllServeEvents().size());
    }

    @Test
    void propagatesRequestIdToDownstreamAndResponse() {
        DOWNSTREAM.stubFor(get(urlEqualTo("/api/hotels/5")).willReturn(okJson("{\"id\":5}")));

        client.get()
                .uri("/api/hotels/5")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                .header("X-Request-Id", "trace-123")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals("X-Request-Id", "trace-123");

        DOWNSTREAM.verify(
                getRequestedFor(urlEqualTo("/api/hotels/5")).withHeader("X-Request-Id", equalTo("trace-123")));
    }

    @Test
    void doesNotRouteInternalEndpoints() {
        client.get().uri("/internal/bookings/1").exchange().expectStatus().isNotFound();

        client.post()
                .uri("/api/rooms/10/confirm-availability")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isNotFound();

        client.post()
                .uri("/api/rooms/10/release")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isNotFound();

        assertEquals(0, DOWNSTREAM.getAllServeEvents().size());
    }

    @Test
    void doesNotExposeUnknownPaths() {
        client.get().uri("/swagger-ui/index.html").exchange().expectStatus().isNotFound();

        assertEquals(0, DOWNSTREAM.getAllServeEvents().size());
    }

    private static String token() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("user")
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .claim("userId", 1L)
                .claim("roles", List.of("USER"))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return ENCODER.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static JwtEncoder createEncoder() {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }
}
