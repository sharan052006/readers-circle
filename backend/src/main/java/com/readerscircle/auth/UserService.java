package com.readerscircle.auth;

import com.readerscircle.auth.dto.UpdateRoleRequest;
import com.readerscircle.auth.dto.UserDto;
import com.readerscircle.chat.LoungeService;
import com.readerscircle.notifications.NotificationService;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;

/**
 * Admin user management (SPEC-auth-identity.md). Deactivation is soft ({@code active=false});
 * history (memberships, messages) is kept — hard delete is out of scope.
 */
@Service
public class UserService {

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final RefreshSessionRepository sessions;
  private final NotificationService notifications;
  private final LoungeService lounge;

  public UserService(
      UserRepository users,
      PasswordEncoder encoder,
      RefreshSessionRepository sessions,
      NotificationService notifications,
      LoungeService lounge) {
    this.users = users;
    this.encoder = encoder;
    this.sessions = sessions;
    this.notifications = notifications;
    this.lounge = lounge;
  }

  @Transactional(readOnly = true)
  public Page<UserDto> list(@PageableDefault(size = 20) Pageable pageable) {
    return users.findAll(pageable).map(this::toDto);
  }

  /** Filtered list for the User Management screens (all params optional). */
  @Transactional(readOnly = true)
  public Page<UserDto> search(Role role, Boolean active, String query, Pageable pageable) {
    String q = query == null || query.isBlank() ? null : query.trim();
    return users.search(role, active, q, pageable).map(this::toDto);
  }

  @Transactional(readOnly = true)
  public UserDto get(UUID id) {
    return toDto(find(id));
  }

  @Transactional(readOnly = true)
  public UserDto getCurrentUser(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    return toDto(find(UUID.fromString(authentication.getName())));
  }

  @Transactional
  public UserDto updateRole(UUID id, UpdateRoleRequest req) {
    User user = find(id);
    Role previous = user.getRole();
    Role next = req.role();
    if (previous == next) {
      return toDto(user);
    }
    user.setRole(next);
    // Role change forces re-login: bump the session version so every existing
    // refresh token (sv claim) mismatches, and revoke all session rows.
    // The reader UI polls /api/users/me (DB-fresh) and shows the promotion
    // congratulations + "Return to login page" screen.
    user.setTokenVersion(user.getTokenVersion() + 1);
    users.save(user);
    for (RefreshSession s : sessions.findByUserId(user.getId())) {
      if (!s.isRevoked()) {
        s.setRevoked(true);
        sessions.save(s);
      }
    }
    if (next == Role.ORGANIZER) {
      try {
        notifications.notify(
            user.getId(),
            "ROLE_PROMOTION",
            "Congratulations! You have been promoted to Organiser",
            "Your reader account is now an organiser account. Please sign in again to continue to the organiser screen.",
            "/auth");
      } catch (Exception ignored) {
        // Notification must never break the promotion itself.
      }
    } else if (previous == Role.ORGANIZER) {
      try {
        notifications.notify(
            user.getId(),
            "ROLE_DEMOTION",
            "You have been demoted to Reader",
            "Your organiser account is now a reader account. Please sign in again to continue to the reader screens.",
            "/auth");
      } catch (Exception ignored) {
        // Notification must never break the demotion itself.
      }
    }
    // The Organisers Lounge is permanent, but membership is live: a demoted
    // (or deactivated) user loses the group immediately, not on next sync.
    try {
      lounge.purgeNonLeaders();
    } catch (Exception ignored) {
      // Lounge hygiene must never break the role change itself.
    }
    return toDto(user);
  }

  @Transactional
  public void deactivate(UUID id) {
    User user = find(id);
    user.setActive(false);
    users.save(user);
  }

  /** Block (active=false) or unblock/reactivate (active=true) a user. */
  @Transactional
  public UserDto updateStatus(UUID id, boolean active) {
    return updateStatus(id, active, null);
  }

  /**
   * Block with an optional admin reason (stored, shown in the Block User modal
   * history); unblocking clears the reason.
   */
  @Transactional
  public UserDto updateStatus(UUID id, boolean active, String reason) {
    User user = find(id);
    user.setActive(active);
    if (!active) {
      String r = reason == null ? null : reason.trim();
      user.setBlockedReason(r == null || r.isEmpty() ? null : r);
      user.setBlockedAt(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
    } else {
      user.setBlockedReason(null);
      user.setBlockedAt(null);
    }
    return toDto(users.save(user));
  }

  @Transactional(readOnly = true)
  public java.util.List<UserDto> listEligibleOrganizers() {
    return users.findByActiveTrueAndRoleInOrderByNameAsc(java.util.List.of(Role.ORGANIZER, Role.ADMIN))
        .stream()
        .map(this::toDto)
        .toList();
  }

  /** Seed helper for tests/admin tooling (not exposed via REST). */
  @Transactional
  public User create(String name, String email, String password, Role role) {
    if (users.existsByEmailIgnoreCase(email.trim())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
    }
    try {
      // saveAndFlush: force the INSERT inside the try — see AuthService.register.
      return users.saveAndFlush(new User(name.trim(), email.trim(), encoder.encode(password), role));
    } catch (DataIntegrityViolationException race) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
    }
  }

  @Transactional
  public UserDto updateProfile(
      Authentication authentication, String name, String bio, String profilePictureUrl) {
    return updateProfile(authentication, name, null, bio, profilePictureUrl, null, null);
  }

  @Transactional
  public UserDto updateProfile(
      Authentication authentication,
      String name,
      String email,
      String bio,
      String profilePictureUrl) {
    return updateProfile(authentication, name, email, bio, profilePictureUrl, null, null);
  }

  @Transactional
  public UserDto updateProfile(
      Authentication authentication,
      String name,
      String email,
      String bio,
      String profilePictureUrl,
      String city,
      java.util.List<String> interests) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    User user = find(UUID.fromString(authentication.getName()));
    if (name != null && !name.isBlank()) {
      user.setName(name.trim());
    }
    if (email != null && !email.isBlank() && !email.trim().equalsIgnoreCase(user.getEmail())) {
      String normalized = email.trim();
      if (users.existsByEmailIgnoreCase(normalized)) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
      }
      user.setEmail(normalized);
    }
    if (bio != null) {
      user.setBio(bio.trim());
    }
    if (profilePictureUrl != null) {
      user.setProfilePictureUrl(profilePictureUrl.trim());
    }
    if (city != null) {
      user.setCity(city.isBlank() ? null : city.trim());
    }
    if (interests != null) {
      user.setInterests(joinInterests(interests));
    }
    return toDto(users.save(user));
  }

  /** Change own password (current password must match, minimum 8 chars). */
  @Transactional
  public void changePassword(Authentication authentication, String currentPassword, String newPassword) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    if (currentPassword == null || newPassword == null || newPassword.length() < 8 || newPassword.length() > 100) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "New password must be 8-100 characters");
    }
    User user = find(UUID.fromString(authentication.getName()));
    if (!encoder.matches(currentPassword, user.getPasswordHash())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
    }
    if (encoder.matches(newPassword, user.getPasswordHash())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "New password must be different from the current password");
    }
    user.setPasswordHash(encoder.encode(newPassword));
    users.save(user);
  }

  private User find(UUID id) {
    return users
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
  }

  private UserDto toDto(User u) {
    return new UserDto(
        u.getId(),
        u.getName(),
        u.getEmail(),
        u.getRole(),
        u.isActive(),
        u.getCreatedAt(),
        u.getBio(),
        u.getProfilePictureUrl(),
        u.getBlockedReason(),
        u.getBlockedAt(),
        u.isTwoFactorEnabled(),
        u.getCity(),
        splitInterests(u.getInterests()));
  }

  private static java.util.List<String> splitInterests(String interests) {
    if (interests == null || interests.isBlank()) {
      return java.util.List.of();
    }
    java.util.List<String> out = new java.util.ArrayList<>();
    for (String part : interests.split(",")) {
      if (!part.isBlank()) {
        out.add(part.trim());
      }
    }
    return out;
  }

  private static String joinInterests(java.util.List<String> interests) {
    java.util.List<String> clean = new java.util.ArrayList<>();
    for (String t : interests) {
      if (t != null && !t.isBlank() && clean.size() < 20) {
        clean.add(t.trim());
      }
    }
    return clean.isEmpty() ? null : String.join(",", clean);
  }
}
