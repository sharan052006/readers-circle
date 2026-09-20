package com.readerscircle.identity;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  private final JwtAuthFilter jwtFilter;

  public SecurityConfig(JwtAuthFilter jwtFilter) {
    this.jwtFilter = jwtFilter;
  }

  @Bean
  SecurityFilterChain chain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> write(res, 401, "UNAUTHORIZED", "authentication required"))
                    .accessDeniedHandler(
                        (req, res, ex) -> write(res, 403, "FORBIDDEN", "forbidden")))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/api/auth/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/users/me")
                    .authenticated()
                    .requestMatchers("/api/users/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  private static void write(HttpServletResponse res, int status, String code, String msg)
      throws IOException {
    res.setStatus(status);
    res.setContentType("application/json");
    res.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + msg + "\"}");
  }
}
