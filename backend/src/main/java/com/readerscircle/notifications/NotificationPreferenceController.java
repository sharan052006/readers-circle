package com.readerscircle.notifications;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Current user's notification toggles (Notification Settings screen). */
@RestController
@RequestMapping("/api/users/me/notification-preferences")
@PreAuthorize("isAuthenticated()")
public class NotificationPreferenceController {

  private final NotificationPreferenceService service;

  public NotificationPreferenceController(NotificationPreferenceService service) {
    this.service = service;
  }

  @GetMapping
  public Map<String, Object> get(Authentication authentication) {
    return service.get(getUserId(authentication));
  }

  @PutMapping
  public Map<String, Object> update(
      Authentication authentication, @RequestBody(required = false) Map<String, Object> patch) {
    return service.update(getUserId(authentication), patch);
  }

  private UUID getUserId(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    return UUID.fromString(auth.getName());
  }
}
