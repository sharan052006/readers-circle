package com.readerscircle.identity;

import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwt;

  public AuthService(
      UserRepository users,
      RefreshTokenRepository refreshTokens,
      PasswordEncoder passwordEncoder,
      JwtService jwt) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.passwordEncoder = passwordEncoder;
    this.jwt = jwt;
  }

  @Transactional
  public AuthResponse registerReader(RegisterRequest req) {
    String email = User.normalizeEmail(req.email());
    if (users.existsByEmailIgnoreCase(email)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
    }
    User user =
        new User(req.name().trim(), email, passwordEncoder.encode(req.password()), Role.READER);
    users.save(user);
    return issuePair(user);
  }

  @Transactional
  public AuthResponse login(LoginRequest req) {
    String email = User.normalizeEmail(req.email());
    User user =
        users
            .findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
    if (user.isDeactivated()
        || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
    }
    return issuePair(user);
  }

  @Transactional
  public AuthResponse refresh(String refreshToken) {
    String hash = sha256(refreshToken);
    RefreshToken stored =
        refreshTokens
            .findByTokenHash(hash)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh"));
    try {
      var claims = jwt.parse(refreshToken);
      if (!"refresh".equals(claims.get("type", String.class))) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh");
      }
    } catch (JwtException e) {
      stored.setRevoked(true);
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh");
    }
    if (stored.isRevoked() || stored.getExpiresAt().isBefore(OffsetDateTime.now())) {
      stored.setRevoked(true);
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh");
    }
    User user =
        users
            .findById(stored.getUserId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh"));
    if (user.isDeactivated()) {
      stored.setRevoked(true);
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh");
    }
    stored.setRevoked(true);
    return issuePair(user);
  }

  private AuthResponse issuePair(User user) {
    String access = jwt.generateAccess(user);
    String refresh = jwt.generateRefresh();
    RefreshToken rt =
        new RefreshToken(
            user.getId(),
            sha256(refresh),
            OffsetDateTime.now().plusSeconds(jwt.getRefreshSeconds()));
    refreshTokens.save(rt);
    return new AuthResponse(access, refresh, user.getId(), user.getRole().name());
  }

  static String sha256(String v) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(v.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
