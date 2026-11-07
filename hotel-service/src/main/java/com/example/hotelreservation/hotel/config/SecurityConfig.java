package com.example.hotelreservation.hotel.config;

import com.example.hotelreservation.hotel.security.InternalTokenFilter;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(
    HttpSecurity http,
    JwtAuthenticationConverter jwtAuthenticationConverter,
    @Value("${security.internal.token}") String internalToken
  ) throws Exception {
    http
      .csrf(AbstractHttpConfigurer::disable)
      .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
      .sessionManagement(session ->
        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      )
      .authorizeHttpRequests(auth ->
        auth
          .requestMatchers(
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/webjars/**",
            "/actuator/health",
            "/actuator/info",
            "/h2-console/**",
            "/error"
          )
          .permitAll()
          .requestMatchers(
            HttpMethod.POST,
            "/api/rooms/*/confirm-availability",
            "/api/rooms/*/release"
          )
          .permitAll()
          .requestMatchers(HttpMethod.GET, "/api/rooms/statistics")
          .hasRole("ADMIN")
          .requestMatchers(HttpMethod.GET, "/api/hotels/**", "/api/rooms/**")
          .hasAnyRole("USER", "ADMIN")
          .requestMatchers("/api/hotels/**", "/api/rooms/**")
          .hasRole("ADMIN")
          .anyRequest()
          .authenticated()
      )
      .addFilterBefore(
        new InternalTokenFilter(internalToken),
        AuthorizationFilter.class
      )
      .oauth2ResourceServer(oauth2 ->
        oauth2.jwt(jwt ->
          jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)
        )
      );

    return http.build();
  }

  @Bean
  JwtDecoder jwtDecoder(@Value("${security.jwt.secret}") String secret) {
    SecretKey key = new SecretKeySpec(
      secret.getBytes(StandardCharsets.UTF_8),
      "HmacSHA256"
    );
    return NimbusJwtDecoder.withSecretKey(key)
      .macAlgorithm(MacAlgorithm.HS256)
      .build();
  }

  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authoritiesConverter =
      new JwtGrantedAuthoritiesConverter();
    authoritiesConverter.setAuthoritiesClaimName("roles");
    authoritiesConverter.setAuthorityPrefix("ROLE_");

    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
    return converter;
  }
}
