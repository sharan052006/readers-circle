package com.readerscircle.auth;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless security chain (SPEC-auth-identity.md boundaries).
 * 401 = unauthenticated, 403 = wrong role — enforced here, not per-controller.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private static final List<String> DEFAULT_ORIGINS =
      List.of(
          "http://localhost:5173",
          "http://localhost:5174",
          "http://127.0.0.1:5173",
          "http://127.0.0.1:5174");

  private final JwtAuthFilter jwtAuthFilter;
  private final List<String> allowedOrigins;

  public SecurityConfig(
      JwtAuthFilter jwtAuthFilter,
      @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:5174,http://127.0.0.1:5173,http://127.0.0.1:5174}")
          List<String> allowedOrigins) {
    this.jwtAuthFilter = jwtAuthFilter;
    this.allowedOrigins = allowedOrigins;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOrigins(resolveOrigins());
    c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
    c.setAllowedHeaders(List.of("*"));
    c.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
    s.registerCorsConfiguration("/**", c);
    return s;
  }

  private List<String> resolveOrigins() {
    List<String> raw = this.allowedOrigins == null ? List.of() : this.allowedOrigins;
    List<String> parsed =
        raw.stream()
            .flatMap(s -> Arrays.stream(s.split(",")))
            .map(String::trim)
            .map(s -> s.replaceAll("/+$", ""))
            .filter(s -> !s.isEmpty() && !s.equals("*"))
            .toList();
    return parsed.isEmpty() ? DEFAULT_ORIGINS : parsed;
  }

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.cors(Customizer.withDefaults())
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/auth/**", "/error", "/api/public/**", "/healthz")
                    .permitAll()
                    .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/circles", "/api/circles/*", "/api/circles/*/events", "/api/events", "/api/events/*", "/api/media/**")
                    .permitAll()
                    .requestMatchers("/ws/**", "/ws")
                    .permitAll()
                    .requestMatchers("/api/users/me", "/api/users/me/**")
                    .authenticated()
                    .requestMatchers("/api/users/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                    .accessDeniedHandler(
                        (req, res, ex) -> res.sendError(HttpServletResponse.SC_FORBIDDEN)))
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
