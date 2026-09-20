package com.readerscircle.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.readerscircle.auth.dto.LoginRequest;
import com.readerscircle.auth.dto.RegisterRequest;
import com.readerscircle.auth.dto.TokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Task 4 gate: full HTTP stack (filter chain + statuses) on real Postgres + Flyway V1.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("app.jwt.secret", () -> "integration-test-secret-at-least-32-chars-long");
  }

  @Autowired TestRestTemplate rest;
  @Autowired UserRepository users;

  @Test
  void registerLoginRefresh() {
    var reg =
        rest.postForEntity(
            "/api/auth/register",
            new RegisterRequest("Asha", "asha@x.com", "password123"),
            TokenResponse.class);
    assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(reg.getBody().accessToken()).isNotBlank();
    assertThat(reg.getBody().refreshToken()).isNotBlank();

    var login =
        rest.postForEntity(
            "/api/auth/login",
            new LoginRequest("ASHA@x.com", "password123"),
            TokenResponse.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

    // Reader token on Admin-only endpoint → 403 (proves 401/403 split at HTTP level).
    var forbidden =
        rest.exchange(
            "/api/users",
            HttpMethod.GET,
            new HttpEntity<>(bearer(login.getBody().accessToken())),
            String.class);
    assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    var refreshed =
        rest.postForEntity(
            "/api/auth/refresh",
            new AuthController.RefreshRequest(login.getBody().refreshToken()),
            TokenResponse.class);
    assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(refreshed.getBody().accessToken()).isNotBlank();

    var tampered =
        rest.postForEntity(
            "/api/auth/refresh",
            new AuthController.RefreshRequest(login.getBody().refreshToken() + "x"),
            String.class);
    assertThat(tampered.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    // Access token is not a refresh token → 401.
    var wrongKind =
        rest.postForEntity(
            "/api/auth/refresh",
            new AuthController.RefreshRequest(login.getBody().accessToken()),
            String.class);
    assertThat(wrongKind.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void duplicateEmailConflictsAndPasswordHashedAsReader() {
    rest.postForEntity(
        "/api/auth/register", new RegisterRequest("A", "dup@x.com", "password123"), TokenResponse.class);

    var dup =
        rest.postForEntity(
            "/api/auth/register",
            new RegisterRequest("B", "DUP@X.com", "password123"),
            String.class);
    assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

    User saved = users.findByEmailIgnoreCase("dup@x.com").orElseThrow();
    assertThat(saved.getRole()).isEqualTo(Role.READER);
    assertThat(saved.getPasswordHash()).isNotEqualTo("password123");
    assertThat(saved.getPasswordHash()).startsWith("$2");
  }

  @Test
  void badCredentialsAndDeactivatedAre401() {
    rest.postForEntity(
        "/api/auth/register", new RegisterRequest("C", "c@x.com", "password123"), TokenResponse.class);

    assertThat(
            rest.postForEntity(
                    "/api/auth/login",
                    new LoginRequest("c@x.com", "wrongpass1"),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            rest.postForEntity(
                    "/api/auth/login",
                    new LoginRequest("ghost@x.com", "password123"),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);

    User user = users.findByEmailIgnoreCase("c@x.com").orElseThrow();
    user.setActive(false);
    users.save(user);
    assertThat(
            rest.postForEntity(
                    "/api/auth/login",
                    new LoginRequest("c@x.com", "password123"),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  private static HttpHeaders bearer(String token) {
    var h = new HttpHeaders();
    h.setBearerAuth(token);
    return h;
  }
}
