package com.readerscircle.events;

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

/** Event Chat board on the event detail screen. */
@RestController
@PreAuthorize("isAuthenticated()")
public class EventMessageController {

  private final EventMessageService service;

  public EventMessageController(EventMessageService service) {
    this.service = service;
  }

  public record MessageRequest(String content) {}

  @GetMapping("/api/events/{id}/messages")
  public Map<String, Object> list(
      @PathVariable UUID id, Authentication authentication) {
    return service.list(id, getUserId(authentication), getUserRole(authentication));
  }

  @PostMapping("/api/events/{id}/messages")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> post(
      @PathVariable UUID id,
      @RequestBody(required = false) MessageRequest req,
      Authentication authentication) {
    return service.post(
        id,
        getUserId(authentication),
        getUserRole(authentication),
        req == null ? null : req.content());
  }

  @DeleteMapping("/api/events/{eventId}/messages/{messageId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable UUID eventId,
      @PathVariable UUID messageId,
      Authentication authentication) {
    service.delete(eventId, messageId, getUserId(authentication), getUserRole(authentication));
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
