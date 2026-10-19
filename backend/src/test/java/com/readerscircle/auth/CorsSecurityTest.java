package com.readerscircle.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class CorsSecurityTest {

  @Test
  void defaultOriginsAreAllowedAndCredentialsEnabled() {
    SecurityConfig config = new SecurityConfig(null, null);
    CorsConfigurationSource source = config.corsConfigurationSource();

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/circles");
    CorsConfiguration cors = source.getCorsConfiguration(request);

    assertThat(cors).isNotNull();
    assertThat(cors.getAllowCredentials()).isTrue();

    // Verify all expected local dev origins are accepted
    assertThat(cors.checkOrigin("http://localhost:5174")).isEqualTo("http://localhost:5174");
    assertThat(cors.checkOrigin("http://localhost:5173")).isEqualTo("http://localhost:5173");
    assertThat(cors.checkOrigin("http://127.0.0.1:5174")).isEqualTo("http://127.0.0.1:5174");
    assertThat(cors.checkOrigin("http://127.0.0.1:5173")).isEqualTo("http://127.0.0.1:5173");

    // Verify untrusted origin is strictly rejected
    assertThat(cors.checkOrigin("http://evil.com")).isNull();
    assertThat(cors.checkOrigin("http://attacker.example.org")).isNull();

    // Verify allowed methods include standard and PUT
    assertThat(cors.getAllowedMethods())
        .contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD");

    // Verify wildcard is not in allowed origins
    assertThat(cors.getAllowedOrigins()).doesNotContain("*");
    assertThat(cors.getAllowedOriginPatterns()).isNull();
  }

  @Test
  void customOriginsSanitizedProperlyAndWildcardRejected() {
    // Test with spaces, trailing slash, and wildcard attempt
    SecurityConfig config =
        new SecurityConfig(
            null,
            List.of(
                " https://app.readerscircle.com/ , https://staging.readerscircle.com ",
                "*"));
    CorsConfigurationSource source = config.corsConfigurationSource();

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/auth/login");
    CorsConfiguration cors = source.getCorsConfiguration(request);

    assertThat(cors).isNotNull();
    assertThat(cors.getAllowedOrigins())
        .containsExactlyInAnyOrder(
            "https://app.readerscircle.com", "https://staging.readerscircle.com");

    assertThat(cors.checkOrigin("https://app.readerscircle.com"))
        .isEqualTo("https://app.readerscircle.com");
    assertThat(cors.checkOrigin("https://staging.readerscircle.com"))
        .isEqualTo("https://staging.readerscircle.com");

    // Wildcard was filtered out, so arbitrary origins fail
    assertThat(cors.checkOrigin("http://evil.com")).isNull();
  }

  @Test
  void emptyConfigurationFallsBackToSafeDefaultOrigins() {
    SecurityConfig config = new SecurityConfig(null, List.of("  ", ""));
    CorsConfigurationSource source = config.corsConfigurationSource();

    MockHttpServletRequest request = new MockHttpServletRequest();
    CorsConfiguration cors = source.getCorsConfiguration(request);

    assertThat(cors).isNotNull();
    assertThat(cors.getAllowedOrigins())
        .contains(
            "http://localhost:5173",
            "http://localhost:5174",
            "http://127.0.0.1:5173",
            "http://127.0.0.1:5174");
  }
}
