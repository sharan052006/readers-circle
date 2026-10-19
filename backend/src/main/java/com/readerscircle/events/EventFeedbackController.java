package com.readerscircle.events;

import com.readerscircle.auth.Role;
import java.util.List;
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

/** Past-event review quotes (one per reader, upsert). */
@RestController
@PreAuthorize("isAuthenticated()")
public class EventFeedbackController {

  private final EventFeedbackService service;

  public EventFeedbackController(EventFeedbackService service) {
    this.service = service;
  }

  public record FeedbackRequest(String content, Integer rating) {}

  @GetMapping("/api/events/{id}/feedback")
  public List<Map<String, Object>> list(
      @PathVariable UUID id, Authentication authentication) {
    return service.list(id, getUserId(authentication), getUserRole(authentication));
  }

  @PostMapping("/api/events/{id}/feedback")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> submit(
      @PathVariable UUID id,
      @RequestBody(required = false) FeedbackRequest req,
      Authentication authentication) {
    return service.submit(
        id,
        getUserId(authentication),
        getUserRole(authentication),
        req == null ? null : req.content(),
        req == null ? null : req.rating());
  }

  @DeleteMapping("/api/events/{eventId}/feedback/{feedbackId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable UUID eventId,
      @PathVariable UUID feedbackId,
      Authentication authentication) {
    service.delete(eventId, feedbackId, getUserId(authentication), getUserRole(authentication));
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
