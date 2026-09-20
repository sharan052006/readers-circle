package com.readerscircle.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Task 3 gate: claims, tamper/expiry rejection, fail-fast secret. Pure unit — no container. */
class JwtServiceTest {

  private static final String SECRET = "test-secret-that-is-long-enough-32-chars";

  private final JwtService jwt = new JwtService(SECRET, 15, 7);

  @Test
  void accessTokenCarriesSubAndRole() {
    UUID id = UUID.randomUUID();

    String token = jwt.generateAccessToken(id, Role.ORGANIZER);

    Claims claims = jwt.parse(token).getPayload();
    assertThat(claims.getSubject()).isEqualTo(id.toString());
    assertThat(claims.get("role", String.class)).isEqualTo("ORGANIZER");
    assertThat(jwt.extractUserId(token)).isEqualTo(id);
    assertThat(jwt.extractRole(token)).isEqualTo(Role.ORGANIZER);
  }

  @Test
  void refreshTokenCarriesNoRole() {
    UUID id = UUID.randomUUID();

    Claims claims = jwt.parse(jwt.generateRefreshToken(id)).getPayload();

    assertThat(claims.getSubject()).isEqualTo(id.toString());
    assertThat(claims.get("role")).isNull();
  }

  @Test
  void tamperedTokenRejected() {
    String token = jwt.generateAccessToken(UUID.randomUUID(), Role.READER);

    assertThatThrownBy(() -> jwt.parse(token + "tamper")).isInstanceOf(Exception.class);
  }

  @Test
  void expiredTokenRejected() {
    var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    String expired =
        Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .claim("role", "READER")
            .issuedAt(new Date(System.currentTimeMillis() - 3_600_000))
            .expiration(new Date(System.currentTimeMillis() - 1_000))
            .signWith(key)
            .compact();

    assertThatThrownBy(() -> jwt.parse(expired)).isInstanceOf(ExpiredJwtException.class);
  }

  @Test
  void shortBlankOrPlaceholderSecretFailsFast() {
    assertThatThrownBy(() -> new JwtService("short", 15, 7))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET");
    assertThatThrownBy(() -> new JwtService("   ", 15, 7))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new JwtService(JwtService.PLACEHOLDER, 15, 7))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET");
  }

  @Test
  void appContextFailsToStartWithPlaceholderSecret() {
    var runner =
        new ApplicationContextRunner()
            .withUserConfiguration(JwtService.class)
            .withPropertyValues("app.jwt.secret=" + JwtService.PLACEHOLDER);

    // ApplicationContextRunner records startup failure on the context instead of throwing.
    runner.run(
        ctx -> {
          assertThat(ctx.getStartupFailure()).as("context should fail to start").isNotNull();
          assertThat(ctx.getStartupFailure())
              .rootCause()
              .isInstanceOf(IllegalStateException.class)
              .hasMessageContaining("JWT_SECRET");
        });
  }
}
