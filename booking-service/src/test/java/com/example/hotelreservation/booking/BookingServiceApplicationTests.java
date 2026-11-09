package com.example.hotelreservation.booking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"eureka.client.enabled=false", "booking.recovery.enabled=false"})
class BookingServiceApplicationTests {

    @Test
    void contextLoads() {}
}
