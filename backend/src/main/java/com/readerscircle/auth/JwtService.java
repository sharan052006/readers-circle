package com.readerscircle.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Signs/verifies JWTs (SPEC-auth-identity.md). Access tokens carry {@code sub=userId + role};
 * refresh tokens carry {@code sub} only and can never authorize (filter requires a role claim).
 * Fail-fast: construction rejects a missing/short/placeholder secret so the app cannot boot
 * insecure.
 */
@Service
public class JwtService {

  static final String PLACEHOLDER = "change-me-in-env-min-32-chars-long-placeholder";

  private final SecretKey key;
  private final Duration accessTtl;
  private final Duration refreshTtl;

  public JwtService(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.access-minutes:15}") long accessMinutes,
      @Value("${app.jwt.refresh-days:7}") long refreshDays) {
    if (secret == null
        || secret.isBlank()
        || secret.length() < 32
        || PLACEHOLDER.equals(secret)) {
      throw new IllegalStateException(
          "JWT_SECRET is missing or insecure: set the JWT_SECRET env var to a random string of at least 32 characters");
    }
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTtl = Duration.ofMinutes(accessMinutes);
    this.refreshTtl = Duration.ofDays(refreshDays);
  }

  public String generateAccessToken(UUID userId, Role role) {
    Date now = new Date();
    return Jwts.builder()
        .subject(userId.toString())
        .claim("role", role.name())
        .issuedAt(now)
        .expiration(new Date(now.getTime() + accessTtl.toMillis()))
        .signWith(key)
        .compact();
  }

  public String generateRefreshToken(UUID userId) {
    Date now = new Date();
    return Jwts.builder()
        .subject(userId.toString())
        .issuedAt(now)
        .expiration(new Date(now.getTime() + refreshTtl.toMillis()))
        .signWith(key)
        .compact();
  }

  /** Parses + verifies signature + expiry. Throws {@link JwtException} when invalid. */
  public Jws<Claims> parse(String token) throws JwtException {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
  }

  public UUID extractUserId(String token) {
    return UUID.fromString(parse(token).getPayload().getSubject());
  }

  public Role extractRole(String token) {
    return Role.valueOf(parse(token).getPayload().get("role", String.class));
  }
}
