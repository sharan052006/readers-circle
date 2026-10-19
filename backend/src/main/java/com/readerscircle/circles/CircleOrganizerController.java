package com.readerscircle.circles;

import com.readerscircle.auth.Role;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Circle Organizers screen: current + past organizers; writes are ADMIN-only. */
@RestController
public class CircleOrganizerController {

  private final CircleOrganizerService service;

  public CircleOrganizerController(CircleOrganizerService service) {
    this.service = service;
  }

  public record AddOrganizerRequest(UUID userId) {}

  @GetMapping("/api/circles/{id}/organizers")
  @PreAuthorize("isAuthenticated()")
  public Map<String, Object> list(
      @PathVariable UUID id, Authentication authentication) {
    return service.list(id, getUserId(authentication), getUserRole(authentication), checkIsAdmin(authentication));
  }

  @PostMapping("/api/circles/{id}/organizers")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> add(
      @PathVariable UUID id, @RequestBody AddOrganizerRequest req) {
    if (req == null || req.userId() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
    }
    return service.add(id, req.userId());
  }

  @DeleteMapping("/api/circles/{id}/organizers/{userId}")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(@PathVariable UUID id, @PathVariable UUID userId) {
    service.remove(id, userId);
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

  private boolean checkIsAdmin(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) return false;
    return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
  }
}
