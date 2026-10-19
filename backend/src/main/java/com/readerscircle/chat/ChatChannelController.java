package com.readerscircle.chat;

import com.readerscircle.auth.Role;
import com.readerscircle.circles.Circle;
import com.readerscircle.circles.CircleRepository;
import com.readerscircle.circles.MembershipRepository;
import com.readerscircle.circles.MembershipStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Chat channels for the left panel.
 * READER: returns approved member circles (no lounge).
 * ORGANIZER/ADMIN: returns my circles + Organisers Lounge.
 */
@RestController
@RequestMapping("/api/chat/channels")
@PreAuthorize("isAuthenticated()")
public class ChatChannelController {

  private final LoungeService loungeService;
  private final CircleRepository circles;
  private final MembershipRepository memberships;
  private final ChatService chat;

  public ChatChannelController(
      LoungeService loungeService,
      CircleRepository circles,
      MembershipRepository memberships,
      ChatService chat) {
    this.loungeService = loungeService;
    this.circles = circles;
    this.memberships = memberships;
    this.chat = chat;
  }

  @GetMapping
  public ResponseEntity<Map<String, Object>> channels(Authentication authentication) {
    UUID callerId = getUserId(authentication);
    Role role = getUserRole(authentication);
    Map<String, Object> out = new LinkedHashMap<>();

    List<Map<String, Object>> myChannels = new ArrayList<>();
    myCircles(callerId).forEach(c -> myChannels.add(toChannel(c)));
    out.put("channels", myChannels);

    if (role == Role.ORGANIZER || role == Role.ADMIN) {
      out.put("lounge", loungeService.ensureLounge());
    }
    return ResponseEntity.ok(out);
  }

  /**
   * One call for the reader channel list: last-message preview + time +
   * unread badge + member count per channel (same data admin chat uses).
   */
  @GetMapping("/previews")
  public ResponseEntity<Map<String, Object>> previews(Authentication authentication) {
    UUID callerId = getUserId(authentication);
    Role role = getUserRole(authentication);
    List<Circle> mine = myCircles(callerId);
    Map<String, Long> unreadByCircle = new java.util.HashMap<>();
    for (Map<String, Object> row : chat.unreadCounts(callerId, role)) {
      Object id = row.get("circleId");
      Object n = row.get("unreadCount");
      if (id != null && n instanceof Number num) {
        unreadByCircle.put(String.valueOf(id), num.longValue());
      }
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (Circle c : mine) {
      Map<String, Object> m = toChannel(c);
      m.put("memberCount", memberCount(c));
      m.put("unreadCount", unreadByCircle.getOrDefault(c.getId().toString(), 0L));
      try {
        chat.latestMessage(c.getId(), callerId, role).ifPresentOrElse(
            msg -> {
              m.put("lastMessage", previewText(msg.content()));
              m.put("lastSentAt", msg.sentAt() == null ? null : msg.sentAt().toString());
              m.put("lastSender", msg.senderName());
            },
            () -> {
              m.put("lastMessage", null);
              m.put("lastSentAt", null);
              m.put("lastSender", null);
            });
      } catch (Exception ignored) {
        m.put("lastMessage", null);
        m.put("lastSentAt", null);
        m.put("lastSender", null);
      }
      out.add(m);
    }
    // Most recent conversation first (channels without messages last).
    out.sort((a, b) -> {
      String x = (String) a.get("lastSentAt");
      String y = (String) b.get("lastSentAt");
      if (x == null && y == null) return 0;
      if (x == null) return 1;
      if (y == null) return -1;
      return y.compareTo(x);
    });
    Map<String, Object> wrap = new LinkedHashMap<>();
    wrap.put("channels", out);
    return ResponseEntity.ok(wrap);
  }

  private List<Circle> myCircles(UUID callerId) {
    List<Circle> mine = new ArrayList<>();
    java.util.Set<UUID> seen = new java.util.HashSet<>();
    memberships.findByReaderId(callerId).stream()
        .filter(m -> m.getStatus() == MembershipStatus.APPROVED)
        .forEach(m -> circles.findById(m.getCircleId()).ifPresent(c -> {
          if (!c.isHidden() && seen.add(c.getId())) {
            mine.add(c);
          }
        }));
    // Organized circles also appear even without a membership row.
    circles.findByOrganizerId(callerId).stream()
        .filter(c -> !c.isHidden() && seen.add(c.getId()))
        .forEach(mine::add);
    return mine;
  }

  private long memberCount(Circle c) {
    long approved =
        memberships.countByCircleIdAndStatusAndReaderIdNot(
            c.getId(), MembershipStatus.APPROVED, c.getOrganizerId());
    return approved + 1;
  }

  private static String previewText(String content) {
    if (content == null) return null;
    String t = content.trim().replaceAll("\\s+", " ");
    return t.length() > 80 ? t.substring(0, 80) + "…" : t;
  }

  private static Map<String, Object> toChannel(Circle c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("circleId", c.getId().toString());
    m.put("name", c.getName());
    m.put("city", c.getCity());
    m.put("category", c.getCategory());
    m.put("coverImageUrl", c.getCoverImageUrl());
    return m;
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
