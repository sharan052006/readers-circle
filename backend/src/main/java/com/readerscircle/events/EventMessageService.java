package com.readerscircle.events;

import com.readerscircle.auth.Role;
import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.circles.Circle;
import com.readerscircle.circles.CircleRepository;
import com.readerscircle.circles.MembershipRepository;
import com.readerscircle.circles.MembershipStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Event Chat board on the event detail screen (REST wall, newest last). */
@Service
public class EventMessageService {

  private final EventRepository events;
  private final EventMessageRepository messages;
  private final EventRegistrationRepository registrations;
  private final CircleRepository circles;
  private final MembershipRepository memberships;
  private final UserRepository users;

  public EventMessageService(
      EventRepository events,
      EventMessageRepository messages,
      EventRegistrationRepository registrations,
      CircleRepository circles,
      MembershipRepository memberships,
      UserRepository users) {
    this.events = events;
    this.messages = messages;
    this.registrations = registrations;
    this.circles = circles;
    this.memberships = memberships;
    this.users = users;
  }

  public boolean isChatOpen(Event event) {
    Instant closesAt = getChatClosesAt(event);
    if (closesAt == null) {
      return true;
    }
    return Instant.now().isBefore(closesAt);
  }

  public Instant getChatClosesAt(Event event) {
    if (event == null || event.getEventDate() == null) {
      return null;
    }
    LocalTime time = event.getEventTime() != null ? event.getEventTime() : LocalTime.MIDNIGHT;
    return event.getEventDate().atTime(time).atZone(ZoneId.systemDefault()).toInstant().plus(Duration.ofHours(48));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> list(UUID eventId, UUID actorId, Role actorRole) {
    Event event = findEvent(eventId);
    assertParticipant(event, actorId, actorRole, false);
    List<Map<String, Object>> out = new ArrayList<>();
    for (EventMessage m : messages.findByEventIdOrderByCreatedAtAsc(eventId)) {
      out.add(toDto(m));
    }
    Map<String, Object> wrap = new LinkedHashMap<>();
    wrap.put("messages", out);
    wrap.put("count", out.size());
    wrap.put("chatOpen", isChatOpen(event));
    Instant closesAt = getChatClosesAt(event);
    wrap.put("chatClosesAt", closesAt != null ? closesAt.toString() : null);
    return wrap;
  }

  @Transactional
  public Map<String, Object> post(UUID eventId, UUID authorId, Role authorRole, String content) {
    Event event = findEvent(eventId);
    if (!isChatOpen(event)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Event chat is closed. Chat remains open for only 48 hours after the scheduled event time.");
    }
    if (content == null || content.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message text is required");
    }
    if (content.trim().length() > 2000) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is too long");
    }
    assertParticipant(event, authorId, authorRole, true);
    return toDto(messages.save(new EventMessage(eventId, authorId, content.trim())));
  }

  @Transactional
  public void delete(UUID eventId, UUID messageId, UUID actorId, Role actorRole) {
    EventMessage m =
        messages
            .findById(messageId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
    if (!m.getEventId().equals(eventId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found");
    }
    if (actorRole != Role.ADMIN && !m.getAuthorId().equals(actorId)) {
      Event event = findEvent(eventId);
      Circle circle = circles.findById(event.getCircleId()).orElse(null);
      boolean organizer = circle != null && isEffectiveOrganizer(circle, actorId);
      if (!organizer) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, "Only the author, organizer, or admin can delete messages");
      }
    }
    messages.delete(m);
  }

  private Event findEvent(UUID eventId) {
    return events
        .findById(eventId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
  }

  /**
   * Effective organizer = assigned as the circle's organizer AND still holding
   * a live ORGANIZER/ADMIN role. Role is read from the DB (not the JWT) so a
   * demotion takes effect immediately, even for tokens minted before it.
   */
  private boolean isEffectiveOrganizer(Circle circle, UUID userId) {
    if (circle == null || userId == null || !userId.equals(circle.getOrganizerId())) {
      return false;
    }
    Role role = users.findById(userId).map(User::getRole).orElse(null);
    return role == Role.ORGANIZER || role == Role.ADMIN;
  }

  /** Readers with an active registration, organizer, admin. */
  private void assertParticipant(Event event, UUID actorId, Role actorRole, boolean writing) {
    if (actorRole == Role.ADMIN) {
      return;
    }
    Circle circle = circles.findById(event.getCircleId()).orElse(null);
    if (circle == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found");
    }
    if (isEffectiveOrganizer(circle, actorId)) {
      return;
    }
    if (actorId != null
        && registrations
            .findByEventIdAndReaderId(event.getId(), actorId)
            .map(r -> r.getStatus() == RegistrationStatus.REGISTERED)
            .orElse(false)) {
      return;
    }
    throw new ResponseStatusException(
        HttpStatus.FORBIDDEN,
        writing
            ? "Only registered attendees, circle organizers, or admins can chat here"
            : "Must be a registered attendee, circle organizer, or admin");
  }

  private Map<String, Object> toDto(EventMessage m) {
    User u = users.findById(m.getAuthorId()).orElse(null);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", m.getId().toString());
    out.put("authorId", m.getAuthorId().toString());
    out.put("author", u == null ? "Reader" : u.getName());
    out.put("avatarUrl", u == null ? null : u.getProfilePictureUrl());
    out.put("content", m.getContent());
    out.put("createdAt", m.getCreatedAt() == null ? null : m.getCreatedAt().toString());
    return out;
  }
}
