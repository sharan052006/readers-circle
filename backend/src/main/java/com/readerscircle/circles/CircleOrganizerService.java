package com.readerscircle.circles;

import com.readerscircle.auth.Role;
import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Co-organizer roster backing the Circle Organizers screen (current + past).
 * Reads are member-gated like the members list; writes are ADMIN-only
 * (circles themselves are admin-created).
 */
@Service
public class CircleOrganizerService {

  private final CircleRepository circles;
  private final CircleOrganizerRepository organizers;
  private final MembershipRepository memberships;
  private final UserRepository users;
  private final CircleService circleService;

  public CircleOrganizerService(
      CircleRepository circles,
      CircleOrganizerRepository organizers,
      MembershipRepository memberships,
      UserRepository users,
      CircleService circleService) {
    this.circles = circles;
    this.organizers = organizers;
    this.memberships = memberships;
    this.users = users;
    this.circleService = circleService;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> list(UUID circleId, UUID actorId, Role actorRole, boolean isAdmin) {
    Circle circle =
        circles
            .findById(circleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));
    if ((!circle.isActive() || circle.isHidden()) && !isAdmin) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found");
    }
    assertCanView(circle, actorId, actorRole);
    // Idempotent: primary organizer always present as current.
    circleService.ensureOrganizerRow(circleId, circle.getOrganizerId());

    List<Map<String, Object>> current = new ArrayList<>();
    List<Map<String, Object>> past = new ArrayList<>();
    for (CircleOrganizer o : organizers.findByCircleIdOrderByAddedAtAsc(circleId)) {
      Map<String, Object> dto = toDto(o, circle);
      if (o.isCurrent()) {
        current.add(dto);
      } else {
        past.add(dto);
      }
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("current", current);
    out.put("past", past);
    out.put("currentCount", current.size());
    out.put("pastCount", past.size());
    return out;
  }

  @Transactional
  public Map<String, Object> add(UUID circleId, UUID userId) {
    Circle circle =
        circles
            .findById(circleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));
    User user =
        users
            .findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "User not found"));
    if (user.getRole() != Role.ORGANIZER && user.getRole() != Role.ADMIN) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Appointed user must have ORGANIZER or ADMIN role");
    }
    if (!user.isActive()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is deactivated");
    }
    circleService.ensureOrganizerRow(circleId, circle.getOrganizerId());
    circleService.ensureOrganizerRow(circleId, userId);
    CircleOrganizer row =
        organizers.findByCircleIdAndUserId(circleId, userId).orElseThrow();
    return toDto(row, circle);
  }

  @Transactional
  public void remove(UUID circleId, UUID userId) {
    Circle circle =
        circles
            .findById(circleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));
    if (circle.getOrganizerId().equals(userId)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Primary organizer cannot be removed — reassign the circle first");
    }
    CircleOrganizer row =
        organizers
            .findByCircleIdAndUserId(circleId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organizer not found"));
    if (!row.isCurrent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Already a past organizer");
    }
    if (organizers.countByCircleIdAndRemovedAtIsNull(circleId) <= 1) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Cannot remove the last current organizer");
    }
    row.markRemoved();
    organizers.save(row);
  }

  private void assertCanView(Circle circle, UUID actorId, Role actorRole) {
    // Live-role gate: a demoted organizer (stale organizer_id row) loses access.
    if (actorRole == Role.ADMIN || circleService.isEffectiveOrganizer(circle, actorId)) {
      return;
    }
    boolean member =
        memberships.existsByCircleIdAndReaderIdAndStatus(
            circle.getId(), actorId, MembershipStatus.APPROVED);
    if (!member) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Must be an approved member, circle organizer, or admin to view organizers");
    }
  }

  private Map<String, Object> toDto(CircleOrganizer o, Circle circle) {
    User u = users.findById(o.getUserId()).orElse(null);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("userId", o.getUserId().toString());
    m.put("name", u == null ? "Unknown" : u.getName());
    m.put("email", u == null ? null : u.getEmail());
    m.put("avatarUrl", u == null ? null : u.getProfilePictureUrl());
    m.put("bio", u == null ? null : u.getBio());
    m.put("city", u == null ? null : u.getCity());
    m.put("interests", u == null ? List.of() : splitInterests(u.getInterests()));
    m.put("primary", circle.getOrganizerId().equals(o.getUserId()));
    m.put("current", o.isCurrent());
    m.put("addedAt", o.getAddedAt() == null ? null : o.getAddedAt().toString());
    m.put("removedAt", o.getRemovedAt() == null ? null : o.getRemovedAt().toString());
    m.put(
        "joinedAt",
        u == null || u.getCreatedAt() == null ? null : u.getCreatedAt().toString());
    return m;
  }

  private static List<String> splitInterests(String interests) {
    if (interests == null || interests.isBlank()) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (String part : interests.split(",")) {
      if (!part.isBlank()) {
        out.add(part.trim());
      }
    }
    return out;
  }
}
