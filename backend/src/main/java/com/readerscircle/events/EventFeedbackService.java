package com.readerscircle.events;

import com.readerscircle.auth.Role;
import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Past-event review quotes (one per reader per event, upsert). Readers see
 * feedback for events in circles they belong to; posting needs an active
 * registration on the event.
 */
@Service
public class EventFeedbackService {

  private final EventRepository events;
  private final EventFeedbackRepository feedbacks;
  private final EventRegistrationRepository registrations;
  private final CircleRepository circles;
  private final MembershipRepository memberships;
  private final UserRepository users;

  public EventFeedbackService(
      EventRepository events,
      EventFeedbackRepository feedbacks,
      EventRegistrationRepository registrations,
      CircleRepository circles,
      MembershipRepository memberships,
      UserRepository users) {
    this.events = events;
    this.feedbacks = feedbacks;
    this.registrations = registrations;
    this.circles = circles;
    this.memberships = memberships;
    this.users = users;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> list(UUID eventId, UUID actorId, Role actorRole) {
    Event event = findEvent(eventId);
    assertCanView(event, actorId, actorRole);
    List<Map<String, Object>> out = new ArrayList<>();
    for (EventFeedback f : feedbacks.findByEventIdOrderByCreatedAtDesc(eventId)) {
      out.add(toDto(f));
    }
    return out;
  }

  @Transactional
  public Map<String, Object> submit(
      UUID eventId, UUID readerId, Role readerRole, String content, Integer rating) {
    Event event = findEvent(eventId);
    if (readerRole == Role.ADMIN) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admins do not leave event feedback");
    }
    boolean registered =
        registrations
            .findByEventIdAndReaderId(eventId, readerId)
            .map(r -> r.getStatus() == RegistrationStatus.REGISTERED)
            .orElse(false);
    if (!registered) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only registered attendees can leave feedback");
    }
    if (content == null || content.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Feedback text is required");
    }
    if (rating != null && (rating < 1 || rating > 5)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating must be 1-5");
    }
    EventFeedback fb =
        feedbacks
            .findByEventIdAndReaderId(eventId, readerId)
            .map(
                existing -> {
                  existing.setContent(content.trim());
                  existing.setRating(rating);
                  return existing;
                })
            .orElseGet(
                () -> new EventFeedback(eventId, readerId, content.trim(), rating));
    return toDto(feedbacks.save(fb));
  }

  @Transactional
  public void delete(UUID eventId, UUID feedbackId, UUID actorId, Role actorRole) {
    EventFeedback fb =
        feedbacks
            .findById(feedbackId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found"));
    if (!fb.getEventId().equals(eventId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found");
    }
    if (actorRole != Role.ADMIN && !fb.getReaderId().equals(actorId)) {
      Event event = findEvent(eventId);
      Circle circle = circles.findById(event.getCircleId()).orElse(null);
      boolean organizer = circle != null && isEffectiveOrganizer(circle, actorId);
      if (!organizer) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, "Only the author, organizer, or admin can delete feedback");
      }
    }
    feedbacks.delete(fb);
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

  private void assertCanView(Event event, UUID actorId, Role actorRole) {
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
    boolean member =
        actorId != null
            && memberships
                .findByCircleIdAndReaderId(circle.getId(), actorId)
                .map(m -> m.getStatus() == MembershipStatus.APPROVED)
                .orElse(false);
    if (!member) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Must be an approved member, circle organizer, or admin");
    }
  }

  private Map<String, Object> toDto(EventFeedback f) {
    User u = users.findById(f.getReaderId()).orElse(null);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", f.getId().toString());
    m.put("readerId", f.getReaderId().toString());
    m.put("author", u == null ? "Reader" : u.getName());
    m.put("avatarUrl", u == null ? null : u.getProfilePictureUrl());
    m.put("content", f.getContent());
    m.put("rating", f.getRating());
    m.put("createdAt", f.getCreatedAt() == null ? null : f.getCreatedAt().toString());
    return m;
  }
}
