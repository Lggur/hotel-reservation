package com.example.hotelreservation.booking.security;

import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;

public record CurrentUser(Long id, String username, List<String> roles) {

    public boolean admin() {
        return roles.contains("ADMIN");
    }

    public static CurrentUser from(Jwt jwt) {
        Object userIdClaim = jwt.getClaim("userId");
        Long userId = userIdClaim instanceof Number number ? number.longValue() : null;
        List<String> roles = jwt.getClaimAsStringList("roles");
        return new CurrentUser(userId, jwt.getSubject(), roles == null ? List.of() : List.copyOf(roles));
    }
}
