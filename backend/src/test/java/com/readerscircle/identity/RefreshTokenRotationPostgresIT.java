package com.readerscircle.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Refresh-token rotation / reuse coverage against real PostgreSQL.
 *
 * <p>Skipped automatically when no Docker daemon is available
 * ({@code disabledWithoutDocker}); the equivalent rotation flow is also
 * exercised on H2 in {@link AuthServiceTest} so the suite stays green
 * everywhere.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class RefreshTokenRotationPostgresIT {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void postgresProps(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add(
        "spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
  }

  @Autowired AuthService auth;
  @Autowired RefreshTokenRepository refreshTokens;

  @Test
  void rotationIssuesNewPairRevokesOldAndRejectsReuse() {
    String email = "pg" + System.nanoTime() + "@test.com";
    auth.registerReader(new RegisterRequest("PG", email, "password123"));
    AuthResponse login = auth.login(new LoginRequest(email, "password123"));

    AuthResponse rotated = auth.refresh(login.refreshToken());
    assertThat(rotated.accessToken()).isNotBlank();
    assertThat(rotated.refreshToken()).isNotEqualTo(login.refreshToken());

    RefreshToken oldRow =
        refreshTokens.findByTokenHash(AuthService.sha256(login.refreshToken())).orElseThrow();
    assertThat(oldRow.isRevoked()).isTrue();

    assertThatThrownBy(() -> auth.refresh(login.refreshToken()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("invalid refresh");

    AuthResponse again = auth.refresh(rotated.refreshToken());
    assertThat(again.accessToken()).isNotBlank();
  }
}
