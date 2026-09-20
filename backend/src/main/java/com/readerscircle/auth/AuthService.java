package com.readerscircle.auth;

import com.readerscircle.auth.dto.TokenResponse;
import io.jsonwebtoken.Claims;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

  public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
    this.users = users;
    this.encoder = encoder;
    this.jwt = jwt;
  }

  /** Registers a READER. Duplicate email (any case) → 409, including under race. */
  @Transactional
  public TokenResponse register(String name, String email, String password) {
    String normalized = email.trim();
    if (users.existsByEmailIgnoreCase(normalized)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
    }
    try {
      // saveAndFlush (not save): the INSERT must execute inside this try — with plain save
      // it fires at commit, past the catch, and the race loser would get a 500.
      User user =
          users.saveAndFlush(
              new User(name.trim(), normalized, encoder.encode(password), Role.READER));
      return tokens(user);
    } catch (DataIntegrityViolationException race) {
      // Two requests passed the check simultaneously; the loser hits ux_users_email_lower.
      throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
    }
  }

  /** Wrong credentials, unknown email, or deactivated account → 401 (no reason leaked). */
  @Transactional(readOnly = true)
  public TokenResponse login(String email, String password) {
    User user =
        users.findByEmailIgnoreCase(email.trim())
            .filter(User::isActive)
            .filter(u -> encoder.matches(password, u.getPasswordHash()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
    return tokens(user);
  }

  /**
   * Rotates the pair. Access tokens (carry {@code role}) are rejected here — only
   * role-less refresh tokens accepted. Anything invalid → 401.
   */
  @Transactional(readOnly = true)
  public TokenResponse refresh(String refreshToken) {
    UUID userId;
    try {
      Claims claims = jwt.parse(refreshToken).getPayload();
      if (claims.get("role") != null) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token");
      }
      userId = UUID.fromString(claims.getSubject());
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token");
    }
    User user =
        users.findById(userId)
            .filter(User::isActive)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token"));
    return tokens(user);
  }

  private TokenResponse tokens(User user) {
    // Role read from the row on every mint → promotion takes effect immediately.
    return new TokenResponse(
        jwt.generateAccessToken(user.getId(), user.getRole()),
        jwt.generateRefreshToken(user.getId()));
  }
}
