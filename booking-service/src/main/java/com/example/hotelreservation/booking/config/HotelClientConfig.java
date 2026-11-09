package com.example.hotelreservation.booking.config;

import java.time.Duration;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HotelClientConfig {

    @Bean
    RestClient hotelRestClient(
            RestClient.Builder builder,
            @Value("${hotel-service.base-url}") String baseUrl,
            @Value("${hotel-service.connect-timeout:2s}") Duration connectTimeout,
            @Value("${hotel-service.read-timeout:3s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        return builder.baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    String requestId = MDC.get(RequestTraceFilter.REQUEST_ID);
                    if (requestId != null) {
                        request.getHeaders().set(RequestTraceFilter.HEADER, requestId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }
}
