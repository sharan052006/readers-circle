package com.readerscircle.identity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final SecretKey key;
  private final long accessSeconds;
  private final long refreshSeconds;

  public JwtService(
      @Value("${security.jwt.secret:}") String secret,
      @Value("${security.jwt.access-minutes:15}") long accessMinutes,
      @Value("${security.jwt.refresh-days:7}") long refreshDays) {
    String effective =
        (secret == null || secret.isBlank())
            ? "dev-only-insecure-secret-please-set-JWT_SECRET-0123456789"
            : secret;
    if (effective.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalArgumentException("JWT secret must be >= 256 bits");
    }
    this.key = Keys.hmacShaKeyFor(effective.getBytes(StandardCharsets.UTF_8));
    this.accessSeconds = accessMinutes * 60;
    this.refreshSeconds = refreshDays * 24 * 3600;
  }

  public String generateAccess(User user) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(user.getId().toString())
        .claim("email", user.getEmail())
        .claim("role", user.getRole().name())
        .claim("type", "access")
        .id(UUID.randomUUID().toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(accessSeconds)))
        .signWith(key)
        .compact();
  }

  public String generateRefresh() {
    Instant now = Instant.now();
    return Jwts.builder()
        .claim("type", "refresh")
        .id(UUID.randomUUID().toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(refreshSeconds)))
        .signWith(key)
        .compact();
  }

  public Claims parse(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }

  public long getRefreshSeconds() {
    return refreshSeconds;
  }
}
