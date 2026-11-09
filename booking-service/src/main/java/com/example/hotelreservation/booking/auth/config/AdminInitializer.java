package com.example.hotelreservation.booking.auth.config;

import com.example.hotelreservation.booking.auth.entity.AppUser;
import com.example.hotelreservation.booking.auth.entity.Role;
import com.example.hotelreservation.booking.auth.repository.AppUserRepository;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AdminInitializer implements ApplicationRunner {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public AdminInitializer(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.username:admin}") String adminUsername,
            @Value("${app.admin.password:password}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsernameIgnoreCase(adminUsername)) {
            return;
        }

        AppUser admin = new AppUser();
        admin.setUsername(adminUsername);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        admin.setCreatedAt(Instant.now());
        userRepository.save(admin);

        log.warn("Default admin user '{}' was created; change ADMIN_PASSWORD in production", adminUsername);
    }
}
