package com.example.hotelreservation.booking.client;

import com.example.hotelreservation.booking.client.dto.HotelRoom;
import com.example.hotelreservation.booking.client.dto.RoomSlotRequest;
import com.example.hotelreservation.booking.exception.HotelServiceErrorException;
import com.example.hotelreservation.booking.exception.HotelServiceUnavailableException;
import com.example.hotelreservation.booking.exception.RoomUnavailableException;
import com.example.hotelreservation.booking.security.InternalTokenFilter;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HotelServiceClient {

    private final RestClient hotelRestClient;
    private final String internalToken;

    public HotelServiceClient(
            RestClient hotelRestClient,
            @Value("${security.internal.token}") String internalToken
    ) {
        this.hotelRestClient = hotelRestClient;
        this.internalToken = internalToken;
    }

    public List<HotelRoom> recommend(Long hotelId, LocalDate startDate, LocalDate endDate, String authorization) {
        try {
            List<HotelRoom> rooms = hotelRestClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/rooms/recommend")
                                .queryParam("startDate", startDate)
                                .queryParam("endDate", endDate);
                        if (hotelId != null) {
                            uriBuilder.queryParam("hotelId", hotelId);
                        }
                        return uriBuilder.build();
                    })
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<HotelRoom>>() {
                    });
            return rooms == null ? List.of() : rooms;
        } catch (ResourceAccessException exception) {
            throw new HotelServiceUnavailableException("Hotel service is unavailable", exception);
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        }
    }

    public void confirmAvailability(Long roomId, Long bookingId, LocalDate startDate, LocalDate endDate) {
        try {
            hotelRestClient.post()
                    .uri("/api/rooms/{roomId}/confirm-availability", roomId)
                    .header(InternalTokenFilter.HEADER, internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(slotRequest(bookingId, startDate, endDate))
                    .retrieve()
                    .toBodilessEntity();
        } catch (ResourceAccessException exception) {
            throw new HotelServiceUnavailableException("Hotel service is unavailable", exception);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 409 || status == 404) {
                throw new RoomUnavailableException(
                        "Room " + roomId + " is not available for the requested dates");
            }
            throw translate(exception);
        }
    }

    public void release(Long roomId, Long bookingId, LocalDate startDate, LocalDate endDate) {
        try {
            hotelRestClient.post()
                    .uri("/api/rooms/{roomId}/release", roomId)
                    .header(InternalTokenFilter.HEADER, internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(slotRequest(bookingId, startDate, endDate))
                    .retrieve()
                    .toBodilessEntity();
        } catch (ResourceAccessException exception) {
            throw new HotelServiceUnavailableException("Hotel service is unavailable", exception);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is5xxServerError()) {
                throw new HotelServiceUnavailableException("Hotel service failed to release the room", exception);
            }
            throw new HotelServiceErrorException(
                    "Hotel service rejected the release with status " + exception.getStatusCode().value(),
                    exception);
        }
    }

    private RoomSlotRequest slotRequest(Long bookingId, LocalDate startDate, LocalDate endDate) {
        return new RoomSlotRequest(
                bookingId,
                UUID.randomUUID().toString(),
                startDate.toString(),
                endDate.toString());
    }

    private RuntimeException translate(RestClientResponseException exception) {
        if (exception.getStatusCode().is5xxServerError()) {
            return new HotelServiceUnavailableException("Hotel service returned a server error", exception);
        }
        return new HotelServiceErrorException(
                "Hotel service returned status " + exception.getStatusCode().value(), exception);
    }
}
