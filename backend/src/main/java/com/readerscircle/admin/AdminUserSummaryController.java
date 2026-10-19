package com.readerscircle.admin;

import com.readerscircle.auth.Role;
import com.readerscircle.auth.UserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role breakdown backing the Users sidebar (All Users / Organisers / Members)
 * badges without forcing the frontend to page through every user.
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserSummaryController {

  private final UserRepository users;

  public AdminUserSummaryController(UserRepository users) {
    this.users = users;
  }

  @GetMapping("/summary")
  public ResponseEntity<Map<String, Object>> summary() {
    Map<String, Object> m = new LinkedHashMap<>();
    long total = users.count();
    long active = users.countByActiveTrue();
    m.put("totalAdmins", users.countByRole(Role.ADMIN));
    m.put("totalOrganizers", users.countByRole(Role.ORGANIZER));
    m.put("totalReaders", users.countByRole(Role.READER));
    m.put("totalMembers", users.countByRoleIn(List.of(Role.READER, Role.ORGANIZER)));
    m.put("activeMembers", active);
    m.put("totalUsers", total);
    m.put("activeUsers", active);
    m.put("blockedUsers", Math.max(0, total - active));
    m.put(
        "joinedThisMonth",
        users.countByCreatedAtAfter(
            java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
                .withDayOfMonth(1)
                .toLocalDate()
                .atStartOfDay(java.time.ZoneOffset.UTC)
                .toOffsetDateTime()));
    return ResponseEntity.ok(m);
  }
}
