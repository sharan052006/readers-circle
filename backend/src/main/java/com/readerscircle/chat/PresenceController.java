package com.readerscircle.chat;

import com.readerscircle.auth.Role;
import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.circles.Circle;
import com.readerscircle.circles.CircleRepository;
import com.readerscircle.circles.MembershipRepository;
import com.readerscircle.circles.MembershipStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Heartbeats + online organizers/admins for the ACTIVE LEADERS panel. */
@RestController
public class PresenceController {

  private final PresenceService presence;
  private final UserRepository users;
  private final CircleRepository circles;
  private final MembershipRepository memberships;

  public PresenceController(
      PresenceService presence,
      UserRepository users,
      CircleRepository circles,
      MembershipRepository memberships) {
    this.presence = presence;
    this.users = users;
    this.circles = circles;
    this.memberships = memberships;
  }

  @PostMapping("/api/chat/presence")
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void heartbeat(Authentication authentication) {
    presence.heartbeat(getUserId(authentication));
  }

  @GetMapping("/api/chat/online")
  @PreAuthorize("isAuthenticated()")
  public List<Map<String, Object>> online() {    List<User> leaders = users.findByActiveTrueAndRoleInOrderByNameAsc(List.of(Role.ORGANIZER, Role.ADMIN));
    return leaders.stream()
        .map(
            u -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("userId", u.getId().toString());
              m.put("name", u.getName());
              m.put("role", u.getRole().name());
              m.put("online", presence.isOnline(u.getId()));
              Instant seen = presence.lastSeen(u.getId());
              m.put("lastSeen", seen != null ? seen.toString() : null);
              return m;
            })
        .toList();
  }

  /**
   * "N online" in the conversation header: online approved members +
   * organizer of one circle. Member-gated like the members list.
   */
  @GetMapping("/api/circles/{id}/online")
  @PreAuthorize("isAuthenticated()")
  public Map<String, Object> circleOnline(
      @PathVariable UUID id, Authentication authentication) {
    UUID callerId = getUserId(authentication);
    Role role = getUserRole(authentication);
    Circle circle =
        circles
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));
    boolean organizer = callerId != null && isEffectiveOrganizer(circle, callerId);
    boolean admin = role == Role.ADMIN;
    boolean member =
        memberships.existsByCircleIdAndReaderIdAndStatus(
            id, callerId, MembershipStatus.APPROVED);
    if (!organizer && !admin && !member) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Must be an approved member, circle organizer, or admin");
    }
    java.util.Set<UUID> candidates = new java.util.LinkedHashSet<>();
    candidates.add(circle.getOrganizerId());
    memberships.findByCircleIdAndStatus(id, MembershipStatus.APPROVED).stream()
        .map(m -> m.getReaderId())
        .forEach(candidates::add);
    List<Map<String, Object>> online = new java.util.ArrayList<>();
    for (UUID uid : candidates) {
      if (presence.isOnline(uid)) {
        users.findById(uid).ifPresent(u -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("userId", u.getId().toString());
          m.put("name", u.getName());
          m.put("avatarUrl", u.getProfilePictureUrl());
          online.add(m);
        });
      }
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("onlineCount", online.size());
    out.put("online", online.stream().limit(10).toList());
    return out;
  }

  /**
   * Effective organizer = assigned as the circle's organizer AND still holding
   * a live ORGANIZER/ADMIN role. Role is read from the DB (not the JWT) so a
   * demotion takes effect immediately, even for tokens minted before it.
   */
  private boolean isEffectiveOrganizer(Circle circle, UUID callerId) {
    if (circle == null || callerId == null || !callerId.equals(circle.getOrganizerId())) {
      return false;
    }
    Role role = users.findById(callerId).map(User::getRole).orElse(null);
    return role == Role.ORGANIZER || role == Role.ADMIN;
  }

  private Role getUserRole(Authentication auth) {    if (auth == null) return Role.READER;
    if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
      return Role.ADMIN;
    }
    if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZER"))) {
      return Role.ORGANIZER;
    }
    return Role.READER;
  }

  private UUID getUserId(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    return UUID.fromString(auth.getName());
  }
}
