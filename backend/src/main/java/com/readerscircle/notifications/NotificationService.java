package com.readerscircle.notifications;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Inbox service for the reader bell: list / unread-count / mark read. */
@Service
public class NotificationService {

  private final NotificationRepository notifications;

  public NotificationService(NotificationRepository notifications) {
    this.notifications = notifications;
  }

  @Transactional
  public Notification notify(UUID userId, String type, String title, String body, String link) {
    if (userId == null || title == null || title.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId and title are required");
    }
    return notifications.save(new Notification(userId, type, title.trim(), body, link));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> list(UUID userId) {
    return notifications.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(this::toDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public Map<String, Object> unreadCount(UUID userId) {
    return Map.of("unreadCount", notifications.countByUserIdAndReadFalse(userId));
  }

  @Transactional
  public Map<String, Object> markRead(UUID userId, UUID id) {
    Notification n = notifications.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    if (!n.getUserId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your notification");
    }
    n.markRead();
    notifications.save(n);
    return toDto(n);
  }

  @Transactional
  public Map<String, Object> markAllRead(UUID userId) {
    List<Notification> all = notifications.findByUserIdOrderByCreatedAtDesc(userId);
    for (Notification n : all) {
      if (!n.isRead()) {
        n.markRead();
        notifications.save(n);
      }
    }
    return Map.of("unreadCount", 0L);
  }

  private Map<String, Object> toDto(Notification n) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", n.getId().toString());
    m.put("type", n.getType());
    m.put("title", n.getTitle());
    m.put("body", n.getBody());
    m.put("link", n.getLink());
    m.put("read", n.isRead());
    m.put("isRead", n.isRead());
    m.put("createdAt", n.getCreatedAt() == null ? null : n.getCreatedAt().toString());
    return m;
  }
}
