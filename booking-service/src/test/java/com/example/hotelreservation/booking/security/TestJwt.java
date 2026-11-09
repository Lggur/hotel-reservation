package com.example.hotelreservation.booking.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

public final class TestJwt {

    public static final String SECRET = "hotel-reservation-dev-secret-key-please-change-2026";
    public static final String INTERNAL_TOKEN = "hotel-reservation-internal-token";
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private static final JwtEncoder ENCODER = createEncoder();

    private TestJwt() {}

    public static String user(long userId) {
        return token("user-" + userId, userId, "USER");
    }

    public static String admin() {
        return token("admin", 1L, "ADMIN");
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    public static String token(String subject, long userId, String... roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .claim("userId", userId)
                .claim("roles", List.of(roles))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return ENCODER.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static JwtEncoder createEncoder() {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }
}
