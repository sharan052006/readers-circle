package com.readerscircle.notifications;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persisted per-user notification toggles (all default ON). */
@Service
public class NotificationPreferenceService {

  private final NotificationPreferenceRepository prefs;

  public NotificationPreferenceService(NotificationPreferenceRepository prefs) {
    this.prefs = prefs;
  }

  @Transactional
  public Map<String, Object> get(UUID userId) {
    return toMap(getOrCreate(userId));
  }

  @Transactional
  public Map<String, Object> update(UUID userId, Map<String, Object> patch) {
    NotificationPreference p = getOrCreate(userId);
    if (patch != null) {
      if (patch.containsKey("joinRequest")) p.setJoinRequest(bool(patch.get("joinRequest"), p.isJoinRequest()));
      if (patch.containsKey("memberJoined")) p.setMemberJoined(bool(patch.get("memberJoined"), p.isMemberJoined()));
      if (patch.containsKey("eventCreated")) p.setEventCreated(bool(patch.get("eventCreated"), p.isEventCreated()));
      if (patch.containsKey("eventRegistration"))
        p.setEventRegistration(bool(patch.get("eventRegistration"), p.isEventRegistration()));
      if (patch.containsKey("bookRecommendation"))
        p.setBookRecommendation(bool(patch.get("bookRecommendation"), p.isBookRecommendation()));
      if (patch.containsKey("discussion")) p.setDiscussion(bool(patch.get("discussion"), p.isDiscussion()));
      if (patch.containsKey("report")) p.setReport(bool(patch.get("report"), p.isReport()));
      if (patch.containsKey("chatMessage")) p.setChatMessage(bool(patch.get("chatMessage"), p.isChatMessage()));
      if (patch.containsKey("replies")) p.setReplies(bool(patch.get("replies"), p.isReplies()));
    }
    p.setUpdatedAt(Instant.now());
    return toMap(prefs.save(p));
  }

  private NotificationPreference getOrCreate(UUID userId) {
    return prefs.findById(userId).orElseGet(() -> prefs.save(new NotificationPreference(userId)));
  }

  private Map<String, Object> toMap(NotificationPreference p) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("joinRequest", p.isJoinRequest());
    m.put("memberJoined", p.isMemberJoined());
    m.put("eventCreated", p.isEventCreated());
    m.put("eventRegistration", p.isEventRegistration());
    m.put("bookRecommendation", p.isBookRecommendation());
    m.put("discussion", p.isDiscussion());
    m.put("report", p.isReport());
    m.put("chatMessage", p.isChatMessage());
    m.put("replies", p.isReplies());
    return m;
  }

  private boolean bool(Object v, boolean fallback) {
    if (v instanceof Boolean b) {
      return b;
    }
    if (v instanceof String s) {
      return "true".equalsIgnoreCase(s.trim());
    }
    return fallback;
  }
}
