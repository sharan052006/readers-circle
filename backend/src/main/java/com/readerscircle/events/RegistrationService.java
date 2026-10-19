package com.readerscircle.events;

import com.readerscircle.auth.Role;
import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.circles.Circle;
import com.readerscircle.circles.CircleRepository;
import com.readerscircle.circles.Membership;
import com.readerscircle.circles.MembershipRepository;
import com.readerscircle.circles.MembershipStatus;
import com.readerscircle.events.dto.AttendeeDto;
import com.readerscircle.events.dto.RegistrationDto;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegistrationService {

  private final EventRepository eventRepository;
  private final EventRegistrationRepository registrationRepository;
  private final CircleRepository circleRepository;
  private final MembershipRepository membershipRepository;
  private final UserRepository userRepository;
  private final Clock clock;

  public RegistrationService(
      EventRepository eventRepository,
      EventRegistrationRepository registrationRepository,
      CircleRepository circleRepository,
      MembershipRepository membershipRepository,
      UserRepository userRepository,
      Clock clock) {
    this.eventRepository = eventRepository;
    this.registrationRepository = registrationRepository;
    this.circleRepository = circleRepository;
    this.membershipRepository = membershipRepository;
    this.userRepository = userRepository;
    this.clock = clock;
  }

  @Transactional
  public RegistrationDto register(UUID eventId, UUID readerId, Role readerRole) {
    // 1. Pessimistic lock row on the event
    Event event =
        eventRepository
            .findByIdForUpdate(eventId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    // 2. Fetch Circle and assert circle membership (FR-11: members only, organizer or admin)
    Circle circle =
        circleRepository
            .findById(event.getCircleId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));

    if (!circle.isActive()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot register for an event in an inactive circle");
    }

    boolean readerActive =
        userRepository.findById(readerId).map(User::isActive).orElse(false);
    if (!readerActive) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Your account has been blocked by the administrator. You cannot participate in events.");
    }

    assertApprovedMemberOrOrganizer(circle, readerId, readerRole);

    // 3. Assert event is registrable (PUBLISHED + deadline not passed)
    Instant now = clock.instant();
    try {
      event.assertRegistrable(now);
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    // 4. Check capacity constraint atomically under the lock
    long activeCount =
        registrationRepository.countByEventIdAndStatus(eventId, RegistrationStatus.REGISTERED);
    if (event.getCapacity() != null && activeCount >= event.getCapacity()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Event is at full capacity");
    }

    // 5. Handle registration record (reactivate if previously cancelled, or create new)
    try {
      EventRegistration reg =
          registrationRepository
              .findByEventIdAndReaderId(eventId, readerId)
              .map(existing -> {
                if (existing.getStatus() == RegistrationStatus.REGISTERED) {
                  throw new ResponseStatusException(HttpStatus.CONFLICT, "Already registered for this event");
                }
                existing.reactivate();
                return existing;
              })
              .orElseGet(() -> new EventRegistration(eventId, readerId));

      EventRegistration saved = registrationRepository.saveAndFlush(reg);
      return toRegistrationDto(saved, event);
    } catch (DataIntegrityViolationException race) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Already registered for this event");
    }
  }

  @Transactional
  public RegistrationDto cancelRegistration(UUID eventId, UUID readerId) {
    Event event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    if (event.getStatus() != EventStatus.PUBLISHED) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "Cannot cancel registration for an event that is " + event.getStatus());
    }

    Instant now = clock.instant();
    if (event.getRegistrationDeadline() != null && now.isAfter(event.getRegistrationDeadline())) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "Cannot cancel registration after the registration deadline has passed");
    }

    EventRegistration reg =
        registrationRepository
            .findByEventIdAndReaderId(eventId, readerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registration not found"));

    if (reg.getStatus() == RegistrationStatus.CANCELLED) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration is already cancelled");
    }

    reg.cancel();
    EventRegistration saved = registrationRepository.save(reg);
    return toRegistrationDto(saved, event);
  }

  @Transactional(readOnly = true)
  public List<AttendeeDto> getAttendees(UUID eventId, UUID actorId, Role actorRole) {
    Event event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    Circle circle =
        circleRepository
            .findById(event.getCircleId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));

    if (actorRole != Role.ADMIN && !isEffectiveOrganizer(circle, actorId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the circle organizer or an admin can view attendees");
    }

    List<EventRegistration> activeRegs =
        registrationRepository.findByEventIdAndStatusOrderByRegisteredAtAsc(
            eventId, RegistrationStatus.REGISTERED);

    return activeRegs.stream()
        .map(reg -> {
          User user =
              userRepository
                  .findById(reg.getReaderId())
                  .orElse(null);
          String name = user != null ? user.getName() : "Unknown Reader";
          String email = user != null ? user.getEmail() : "";
          String avatar = user != null ? user.getProfilePictureUrl() : null;
          return new AttendeeDto(reg.getReaderId(), name, email, reg.getRegisteredAt(), avatar, reg.isAttended());
        })
        .toList();
  }

  /**
   * Member-visible roster preview for the event cards/detail (names + faces,
   * no emails). Any approved circle member, the organizer, or an admin.
   */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> getAttendeePreview(UUID eventId, UUID actorId, Role actorRole) {
    Event event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    Circle circle =
        circleRepository
            .findById(event.getCircleId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));
    assertCanViewRoster(circle, actorId, actorRole);
    List<Map<String, Object>> out = new java.util.ArrayList<>();
    for (EventRegistration reg :
        registrationRepository.findByEventIdAndStatusOrderByRegisteredAtAsc(
            eventId, RegistrationStatus.REGISTERED)) {
      User user = userRepository.findById(reg.getReaderId()).orElse(null);
      Map<String, Object> m = new java.util.LinkedHashMap<>();
      m.put("readerId", reg.getReaderId().toString());
      m.put("name", user != null ? user.getName() : "Reader");
      m.put("avatarUrl", user == null ? null : user.getProfilePictureUrl());
      m.put("attended", reg.isAttended());
      out.add(m);
    }
    return out;
  }

  /**
   * Organizer/admin marks a registered reader attended or not (Past tab
   * Attended / Did Not Attend).
   */
  @Transactional
  public AttendeeDto markAttendance(
      UUID eventId, UUID readerId, boolean attended, UUID actorId, Role actorRole) {
    Event event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    Circle circle =
        circleRepository
            .findById(event.getCircleId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));
    if (actorRole != Role.ADMIN && !isEffectiveOrganizer(circle, actorId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the circle organizer or an admin can mark attendance");
    }
    EventRegistration reg =
        registrationRepository
            .findByEventIdAndReaderId(eventId, readerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registration not found"));
    try {
      reg.markAttended(attended);
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }
    EventRegistration saved = registrationRepository.save(reg);
    User user = userRepository.findById(readerId).orElse(null);
    return new AttendeeDto(
        readerId,
        user != null ? user.getName() : "Unknown Reader",
        user != null ? user.getEmail() : "",
        saved.getRegisteredAt(),
        user != null ? user.getProfilePictureUrl() : null,
        saved.isAttended());
  }

  /**
   * Event Stats rails: my registered/upcoming/completed/attended/missed plus
   * total attendees across my events.
   */
  @Transactional(readOnly = true)
  public Map<String, Long> getMyEventStats(UUID readerId) {
    List<EventRegistration> regs =
        registrationRepository.findByReaderIdAndStatusOrderByRegisteredAtDesc(
            readerId, RegistrationStatus.REGISTERED);
    long upcoming = 0;
    long completed = 0;
    long attended = 0;
    long totalAttendees = 0;
    LocalDate today = LocalDate.now(clock);
    LocalTime nowTime = LocalTime.now(clock);
    java.util.Set<UUID> counted = new java.util.HashSet<>();
    for (EventRegistration reg : regs) {
      Event event = eventRepository.findById(reg.getEventId()).orElse(null);
      if (event == null) {
        continue;
      }
      // Datetime-aware like the listings: a passed PUBLISHED event counts as
      // completed even before anyone marks it COMPLETED.
      if (event.getStatus() == EventStatus.COMPLETED
          || (event.getStatus() == EventStatus.PUBLISHED
              && EventService.isPastDateTime(event, today, nowTime))) {
        completed++;
        attended++;
      } else if (event.getStatus() == EventStatus.PUBLISHED) {
        upcoming++;
      }
      if (counted.add(event.getId())) {
        totalAttendees +=
            registrationRepository.countByEventIdAndStatus(event.getId(), RegistrationStatus.REGISTERED);
      }
    }
    Map<String, Long> out = new java.util.LinkedHashMap<>();
    out.put("registered", (long) regs.size());
    out.put("upcoming", upcoming);
    out.put("completed", completed);
    out.put("attended", attended);
    out.put("missed", completed - attended);
    out.put("totalAttendees", totalAttendees);
    return out;
  }

  private void assertCanViewRoster(Circle circle, UUID actorId, Role actorRole) {
    if (actorRole == Role.ADMIN) {
      return;
    }
    if (actorId != null && isEffectiveOrganizer(circle, actorId)) {
      return;
    }
    boolean member =
        actorId != null
            && membershipRepository
                .findByCircleIdAndReaderId(circle.getId(), actorId)
                .map(m -> m.getStatus() == MembershipStatus.APPROVED)
                .orElse(false);
    if (!member) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Must be an approved member, circle organizer, or admin to view attendees");
    }
  }

  @Transactional(readOnly = true)
  public List<RegistrationDto> getMyRegistrations(UUID readerId) {
    List<EventRegistration> regs =
        registrationRepository.findByReaderIdAndStatusOrderByRegisteredAtDesc(
            readerId, RegistrationStatus.REGISTERED);

    return regs.stream()
        .map(reg -> {
          Event event = eventRepository.findById(reg.getEventId()).orElse(null);
          return toRegistrationDto(reg, event);
        })
        .toList();
  }

  private void assertApprovedMemberOrOrganizer(Circle circle, UUID readerId, Role readerRole) {
    if (readerRole == Role.ADMIN) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Admins do not register for events; view attendee roster instead");
    }
    if (isEffectiveOrganizer(circle, readerId)) {
      return;
    }
    Membership membership =
        membershipRepository
            .findByCircleIdAndReaderId(circle.getId(), readerId)
            .orElse(null);

    if (membership == null || membership.getStatus() != MembershipStatus.APPROVED) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only approved members of this circle can register for its events");
    }
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
    Role role = userRepository.findById(userId).map(User::getRole).orElse(null);
    return role == Role.ORGANIZER || role == Role.ADMIN;
  }

  private RegistrationDto toRegistrationDto(EventRegistration reg, Event event) {
    String title = event != null ? event.getTitle() : "Unknown Event";
    UUID circleId = event != null ? event.getCircleId() : null;
    String circleName = null;
    if (circleId != null) {
      circleName = circleRepository.findById(circleId).map(Circle::getName).orElse(null);
    }
    long registeredCount = 0;
    if (event != null) {
      registeredCount =
          registrationRepository.countByEventIdAndStatus(event.getId(), RegistrationStatus.REGISTERED);
    }
    return new RegistrationDto(
        reg.getId(),
        reg.getEventId(),
        title,
        circleId,
        reg.getReaderId(),
        reg.getStatus(),
        reg.getRegisteredAt(),
        reg.getCancelledAt(),
        event != null ? event.getEventDate() : null,
        event != null ? event.getEventTime() : null,
        event != null ? event.getVenue() : null,
        event != null ? event.getCoverImageUrl() : null,
        circleName,
        event != null ? event.getCapacity() : null,
        registeredCount,
        reg.isAttended());
  }
}
