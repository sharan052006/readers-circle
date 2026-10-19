package com.readerscircle.auth;

import com.readerscircle.auth.dto.TokenResponse;
import io.jsonwebtoken.Claims;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * Auth use-cases (SPEC-auth-identity.md). Errors use {@link ResponseStatusException} so statuses
 * are correct without a handler; Task 5 centralizes error bodies without changing statuses.
 */
@Service
public class AuthService {

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final JwtService jwt;
  private final TotpService totp;
  private final RefreshSessionRepository sessions;

  public AuthService(
      UserRepository users,
      PasswordEncoder encoder,
      JwtService jwt,
      TotpService totp,
      RefreshSessionRepository sessions) {
    this.users = users;
    this.encoder = encoder;
    this.jwt = jwt;
    this.totp = totp;
    this.sessions = sessions;
  }

  /** Registers a READER. Duplicate email (any case) → 409, including under race. */
  @Transactional
  public TokenResponse register(String name, String email, String password) {
    return register(name, email, password, null, null);
  }

  @Transactional
  public TokenResponse register(
      String name, String email, String password, String bio, String profilePictureUrl) {
    return register(name, email, password, bio, profilePictureUrl, null, null);
  }

  @Transactional
  public TokenResponse register(
      String name,
      String email,
      String password,
      String bio,
      String profilePictureUrl,
      String city,
      java.util.List<String> interests) {
    String normalized = email.trim();
    if (users.existsByEmailIgnoreCase(normalized)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
    }
    try {
      // saveAndFlush (not save): the INSERT must execute inside this try — with plain save
      // it fires at commit, past the catch, and the race loser would get a 500.
      User user = new User(
              name.trim(),
              normalized,
              encoder.encode(password),
              Role.READER,
              bio != null ? bio.trim() : null,
              profilePictureUrl != null ? profilePictureUrl.trim() : null);
      if (city != null && !city.isBlank()) {
        user.setCity(city.trim());
      }
      if (interests != null && !interests.isEmpty()) {
        user.setInterests(joinTags(interests));
      }
      users.saveAndFlush(user);
      return tokens(user);
    } catch (DataIntegrityViolationException race) {
      // Two requests passed the check simultaneously; the loser hits ux_users_email_lower.
      throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
    }
  }

  private static String joinTags(java.util.List<String> tags) {
    java.util.List<String> clean = new java.util.ArrayList<>();
    for (String t : tags) {
      if (t != null && !t.isBlank() && clean.size() < 20) {
        clean.add(t.trim());
      }
    }
    return clean.isEmpty() ? null : String.join(",", clean);
  }

  /** Wrong credentials, unknown email, or deactivated account → 401 (no reason leaked). */
  @Transactional
  public TokenResponse login(String email, String password) {
    User user =
        users.findByEmailIgnoreCase(email.trim())
            .filter(User::isActive)
            .filter(u -> encoder.matches(password, u.getPasswordHash()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
    if (user.isTwoFactorEnabled()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "two-factor code required");
    }
    return tokens(user);
  }

  /** Second step for 2FA accounts: password already verified, code completes login. */
  @Transactional
  public TokenResponse verifyTwoFactor(String email, String code) {
    User user =
        users.findByEmailIgnoreCase(email == null ? "" : email.trim())
            .filter(User::isActive)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
    if (!user.isTwoFactorEnabled()
        || user.getTwoFactorSecret() == null
        || !totp.verify(user.getTwoFactorSecret(), code)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid two-factor code");
    }
    return tokens(user);
  }

  /**
   * Rotates the pair. Access tokens (carry {@code role}) are rejected here — only
   * role-less refresh tokens accepted. Revoked sessions and stale session
   * versions (Sign out of all devices) → 401. Anything invalid → 401.
   */
  @Transactional
  public TokenResponse refresh(String refreshToken) {
    UUID userId;
    int version;
    try {
      Claims claims = jwt.parse(refreshToken).getPayload();
      if (claims.get("role") != null) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token");
      }
      userId = UUID.fromString(claims.getSubject());
      Object sv = claims.get("sv");
      version = sv instanceof Number n ? n.intValue() : 0;
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token");
    }
    User user =
        users.findById(userId)
            .filter(User::isActive)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token"));
    if (version != user.getTokenVersion()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token");
    }
    String hash = sha256(refreshToken);
    RefreshSession existing = sessions.findByTokenHash(hash).orElse(null);
    if (existing != null) {
      if (existing.isRevoked() || !existing.getUserId().equals(userId)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token");
      }
      existing.setRevoked(true);
      sessions.save(existing);
    }
    return tokens(user);
  }

  private TokenResponse tokens(User user) {
    // Role read from the row on every mint → promotion takes effect immediately.
    String refresh = jwt.generateRefreshToken(user.getId(), user.getTokenVersion());
    sessions.save(new RefreshSession(user.getId(), sha256(refresh), deviceInfo(), clientIp()));
    return new TokenResponse(
        jwt.generateAccessToken(user.getId(), user.getRole()),
        refresh);
  }

  /** Active (non-revoked) sessions for View Sessions. */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> listSessions(UUID userId) {
    return sessions.findByUserIdAndRevokedFalseOrderByCreatedAtDesc(userId).stream()
        .map(
            s -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("id", s.getId().toString());
              m.put("device", s.getDeviceInfo() == null ? "Unknown device" : s.getDeviceInfo());
              m.put("ip", s.getIpAddress());
              m.put("createdAt", s.getCreatedAt() == null ? null : s.getCreatedAt().toString());
              m.put("lastUsedAt", s.getLastUsedAt() == null ? null : s.getLastUsedAt().toString());
              return m;
            })
        .toList();
  }

  /** Revoke one session (View Sessions → revoke). */
  @Transactional
  public void revokeSession(UUID userId, UUID sessionId) {
    RefreshSession s =
        sessions
            .findById(sessionId)
            .filter(r -> r.getUserId().equals(userId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found"));
    s.setRevoked(true);
    sessions.save(s);
  }

  /** Sign out of all devices: bump the session version + revoke every session row. */
  @Transactional
  public void signOutAll(UUID userId) {
    User user =
        users
            .findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
    user.setTokenVersion(user.getTokenVersion() + 1);
    users.save(user);
    for (RefreshSession s : sessions.findByUserId(userId)) {
      s.setRevoked(true);
      sessions.save(s);
    }
  }

  /** Begin 2FA setup: fresh secret + otpauth URL for the authenticator app. */
  @Transactional
  public Map<String, Object> setupTwoFactor(UUID userId) {
    User user =
        users
            .findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
    if (user.getTwoFactorSecret() == null || user.isTwoFactorEnabled()) {
      user.setTwoFactorSecret(totp.generateSecret());
      user.setTwoFactorEnabled(false);
      users.save(user);
    }
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("secret", user.getTwoFactorSecret());
    m.put("otpauthUrl", totp.otpauthUrl(user.getTwoFactorSecret(), user.getEmail()));
    m.put("enabled", user.isTwoFactorEnabled());
    return m;
  }

  /** Confirm 2FA setup with a code from the authenticator app. */
  @Transactional
  public Map<String, Object> enableTwoFactor(UUID userId, String code) {
    User user =
        users
            .findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
    if (user.getTwoFactorSecret() == null || !totp.verify(user.getTwoFactorSecret(), code)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid two-factor code");
    }
    user.setTwoFactorEnabled(true);
    users.save(user);
    return Map.of("enabled", true);
  }

  /** Disable 2FA (secret discarded). */
  @Transactional
  public Map<String, Object> disableTwoFactor(UUID userId) {
    User user =
        users
            .findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
    user.setTwoFactorEnabled(false);
    user.setTwoFactorSecret(null);
    users.save(user);
    return Map.of("enabled", false);
  }

  private String deviceInfo() {
    try {
      var attrs = RequestContextHolder.getRequestAttributes();
      if (attrs instanceof ServletRequestAttributes sra) {
        String ua = sra.getRequest().getHeader("User-Agent");
        if (ua != null && !ua.isBlank()) {
          return ua.length() > 500 ? ua.substring(0, 500) : ua;
        }
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private String clientIp() {
    try {
      var attrs = RequestContextHolder.getRequestAttributes();
      if (attrs instanceof ServletRequestAttributes sra) {
        return sra.getRequest().getRemoteAddr();
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  static String sha256(String value) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(64);
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
