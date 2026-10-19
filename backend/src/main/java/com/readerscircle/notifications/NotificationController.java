package com.readerscircle.notifications;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Reader bell inbox. All routes require authentication (any role). */
@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

  private final NotificationService service;

  public NotificationController(NotificationService service) {
    this.service = service;
  }

  @GetMapping
  public List<Map<String, Object>> list(Authentication authentication) {
    return service.list(getUserId(authentication));
  }

  @GetMapping("/unread-count")
  public Map<String, Object> unreadCount(Authentication authentication) {
    return service.unreadCount(getUserId(authentication));
  }

  @PostMapping("/{id}/read")
  public Map<String, Object> markRead(@PathVariable UUID id, Authentication authentication) {
    return service.markRead(getUserId(authentication), id);
  }

  @PostMapping("/read-all")
  public Map<String, Object> markAllRead(Authentication authentication) {
    return service.markAllRead(getUserId(authentication));
  }

  private UUID getUserId(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    return UUID.fromString(auth.getName());
  }
}
