package com.readerscircle.chat;

import com.readerscircle.auth.Role;
import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.circles.Circle;
import com.readerscircle.circles.CircleRepository;
import com.readerscircle.circles.Membership;
import com.readerscircle.circles.MembershipRepository;
import com.readerscircle.circles.MembershipStatus;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Organisers Lounge backing the General Chat "ORGANISERS & ADMIN" section.
 * Implemented as a hidden circle (invisible to readers in lists/detail/join)
 * whose approved members are exactly the active organizers + admins.
 * Idempotent: safe to call on every channels fetch.
 */
@Service
public class LoungeService {

  static final String LOUNGE_NAME = "Organisers Lounge";

  private final CircleRepository circles;
  private final MembershipRepository memberships;
  private final UserRepository users;

  public LoungeService(
      CircleRepository circles, MembershipRepository membershipRepository, UserRepository users) {
    this.circles = circles;
    this.memberships = membershipRepository;
    this.users = users;
  }

  @Transactional
  public Map<String, Object> ensureLounge() {
    List<User> admins = users.findByActiveTrueAndRoleInOrderByNameAsc(List.of(Role.ADMIN));
    if (admins.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No admin bootstrapped");
    }
    User admin = admins.get(0);

    Circle lounge =
        circles
            .findByNameIgnoreCase(LOUNGE_NAME)
            .orElseGet(
                () -> {
                  Circle created =
                      new Circle(
                          LOUNGE_NAME,
                          "Platform",
                          "Private channel for organisers and the platform admin to coordinate.",
                          admin.getId(),
                          null,
                          "Leadership");
                  created.setHidden(true);
                  return circles.save(created);
                });
    if (!lounge.isHidden()) {
      lounge.setHidden(true);
      lounge = circles.save(lounge);
    }
    final UUID loungeId = lounge.getId();
    final UUID organizerId = lounge.getOrganizerId();

    List<User> leaders = users.findByActiveTrueAndRoleInOrderByNameAsc(List.of(Role.ORGANIZER, Role.ADMIN));
    for (User leader : leaders) {
      if (leader.getId().equals(organizerId)) continue;
      Membership membership =
          memberships
              .findByCircleIdAndReaderId(loungeId, leader.getId())
              .orElseGet(() -> new Membership(loungeId, leader.getId()));
      if (membership.getStatus() != MembershipStatus.APPROVED) {
        membership.approve();
        memberships.save(membership);
      }
    }
    // Permanent group, live membership: anyone who is no longer an active
    // organizer/admin (e.g. demoted readers) is removed again.
    purgeNonLeaders(loungeId);

    long approved =
        memberships.countByCircleIdAndStatusAndReaderIdNot(
            lounge.getId(), MembershipStatus.APPROVED, lounge.getOrganizerId());
    String organizerName =
        users.findById(lounge.getOrganizerId()).map(User::getName).orElse("Admin");

    Map<String, Object> dto = new LinkedHashMap<>();
    dto.put("key", "LOUNGE");
    dto.put("circleId", lounge.getId().toString());
    dto.put("name", lounge.getName());
    dto.put("description", lounge.getDescription());
    dto.put("category", lounge.getCategory());
    dto.put("memberCount", approved + 1);
    dto.put("organizerId", lounge.getOrganizerId().toString());
    dto.put("organizerName", organizerName);
    dto.put("coverImageUrl", lounge.getCoverImageUrl());
    dto.put("createdAt", lounge.getCreatedAt() != null ? lounge.getCreatedAt().toString() : null);
    return dto;
  }

  /**
   * Removes lounge memberships of anyone who is not currently an active
   * organizer/admin (demoted or deactivated users). Called on every sync and
   * right after a role change, so revocation never waits on a schedule.
   */
  @Transactional
  public void purgeNonLeaders() {
    Circle lounge = circles.findByNameIgnoreCase(LOUNGE_NAME).orElse(null);
    if (lounge == null) {
      return;
    }
    purgeNonLeaders(lounge.getId());
  }

  @Transactional
  void purgeNonLeaders(UUID loungeId) {
    Circle lounge = circles.findById(loungeId).orElse(null);
    if (lounge == null) {
      return;
    }
    Set<UUID> keep = new LinkedHashSet<>();
    for (User leader
        : users.findByActiveTrueAndRoleInOrderByNameAsc(List.of(Role.ORGANIZER, Role.ADMIN))) {
      keep.add(leader.getId());
    }
    keep.add(lounge.getOrganizerId());
    for (MembershipStatus status : MembershipStatus.values()) {
      for (Membership m : memberships.findByCircleIdAndStatus(loungeId, status)) {
        if (!keep.contains(m.getReaderId())) {
          memberships.delete(m);
        }
      }
    }
  }

  @Transactional(readOnly = true)
  public UUID loungeId() {
    return circles
        .findByNameIgnoreCase(LOUNGE_NAME)
        .map(Circle::getId)
        .orElse(null);
  }
}
