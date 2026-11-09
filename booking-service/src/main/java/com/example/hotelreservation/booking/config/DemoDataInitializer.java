package com.example.hotelreservation.booking.config;

import com.example.hotelreservation.booking.auth.entity.AppUser;
import com.example.hotelreservation.booking.auth.entity.Role;
import com.example.hotelreservation.booking.auth.repository.AppUserRepository;
import com.example.hotelreservation.booking.booking.entity.Booking;
import com.example.hotelreservation.booking.booking.entity.BookingStatus;
import com.example.hotelreservation.booking.booking.repository.BookingRepository;
import java.time.Instant;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class DemoDataInitializer implements ApplicationRunner {

    private static final String DEMO_USERNAME = "demo";
    private static final String DEMO_PASSWORD = "demo123";

    private final AppUserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataInitializer(
            AppUserRepository userRepository, BookingRepository bookingRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppUser demoUser =
                userRepository.findByUsernameIgnoreCase(DEMO_USERNAME).orElseGet(() -> userRepository.save(demoUser()));

        if (bookingRepository.count() > 0) {
            return;
        }

        Instant now = Instant.now();
        Booking booking = new Booking();
        booking.setUserId(demoUser.getId());
        booking.setHotelId(1L);
        booking.setRoomId(1L);
        booking.setStartDate(LocalDate.now().plusDays(1));
        booking.setEndDate(LocalDate.now().plusDays(4));
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setCreatedAt(now);
        booking.setUpdatedAt(now);
        bookingRepository.save(booking);

        log.info("Demo data created: user '{}' with a confirmed booking", DEMO_USERNAME);
    }

    private AppUser demoUser() {
        AppUser user = new AppUser();
        user.setUsername(DEMO_USERNAME);
        user.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        user.setRole(Role.USER);
        user.setCreatedAt(Instant.now());
        return user;
    }
}
