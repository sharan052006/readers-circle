package com.readerscircle.dashboard;

import com.readerscircle.auth.UserService;
import com.readerscircle.notifications.NotificationPreferenceService;
import java.util.LinkedHashMap;
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

/**
 * Reader-facing settings: GET/PUT /api/users/me/settings.
 * Combines profile (name/bio/photo) + notification toggles in one payload
 * for the Settings left-nav screen.
 */
@RestController
@RequestMapping("/api/users/me/settings")
@PreAuthorize("isAuthenticated()")
public class ReaderSettingsController {

  private final UserService users;
  private final NotificationPreferenceService prefs;

  public ReaderSettingsController(UserService users, NotificationPreferenceService prefs) {
    this.users = users;
    this.prefs = prefs;
  }

  @GetMapping
  public Map<String, Object> get(Authentication authentication) {
    UUID userId = getUserId(authentication);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("profile", users.get(userId));
    out.put("notifications", prefs.get(userId));
    return out;
  }

  @PutMapping
  public Map<String, Object> update(
      Authentication authentication, @RequestBody(required = false) Map<String, Object> body) {
    UUID userId = getUserId(authentication);
    if (body != null) {
      Object profile = body.get("profile");
      if (profile instanceof Map<?, ?> pm) {
        String name = str(pm.get("name"));
        String bio = pm.get("bio") == null ? null : String.valueOf(pm.get("bio"));
        String photo = pm.get("profilePictureUrl") == null
            ? (pm.get("photo") == null ? null : String.valueOf(pm.get("photo")))
            : String.valueOf(pm.get("profilePictureUrl"));
        // email change allowed here too (same rules as PATCH /api/users/me)
        String email = pm.get("email") == null ? null : String.valueOf(pm.get("email"));
        String city = pm.get("city") == null ? null : String.valueOf(pm.get("city"));
        java.util.List<String> interests = toStringList(pm.get("interests"));
        if (name != null || bio != null || photo != null || email != null || city != null || interests != null) {
          users.updateProfile(authentication, name, email, bio, photo, city, interests);
        }
      }
      Object notif = body.get("notifications");
      if (notif instanceof Map<?, ?> nm) {
        @SuppressWarnings("unchecked")
        Map<String, Object> patch = (Map<String, Object>) nm;
        prefs.update(userId, patch);
      }
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("profile", users.get(userId));
    out.put("notifications", prefs.get(userId));
    return out;
  }

  private static String str(Object v) {
    if (v == null) return null;
    String s = String.valueOf(v).trim();
    return s.isEmpty() ? null : s;
  }

  @SuppressWarnings("unchecked")
  private static java.util.List<String> toStringList(Object v) {
    if (v == null) return null;
    if (v instanceof java.util.List<?> list) {
      java.util.List<String> out = new java.util.ArrayList<>();
      for (Object e : list) {
        if (e != null && !String.valueOf(e).isBlank()) {
          out.add(String.valueOf(e).trim());
        }
      }
      return out;
    }
    String s = String.valueOf(v).trim();
    if (s.isEmpty()) return null;
    return java.util.Arrays.stream(s.split(",")).map(String::trim).filter(p -> !p.isEmpty()).toList();
  }

  private UUID getUserId(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    return UUID.fromString(auth.getName());
  }
}
