package com.readerscircle.dashboard;

import com.readerscircle.auth.Role;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** GET /api/dashboard — reader home aggregate (feed + right rail + my events). */
@RestController
@PreAuthorize("isAuthenticated()")
public class ReaderDashboardController {

  private final ReaderDashboardService service;

  public ReaderDashboardController(ReaderDashboardService service) {
    this.service = service;
  }

  @GetMapping({"/api/dashboard", "/api/users/me/dashboard"})
  public Map<String, Object> dashboard(
      @RequestParam(required = false, defaultValue = "10") int feedLimit,
      Authentication authentication) {
    UUID userId = getUserId(authentication);
    Role role = getUserRole(authentication);
    return service.dashboard(userId, role, feedLimit);
  }

  private UUID getUserId(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    return UUID.fromString(auth.getName());
  }

  private Role getUserRole(Authentication auth) {
    if (auth == null) return Role.READER;
    if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
      return Role.ADMIN;
    }
    if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZER"))) {
      return Role.ORGANIZER;
    }
    return Role.READER;
  }
}
