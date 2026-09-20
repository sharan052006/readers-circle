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
import java.util.List;
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

    if (actorRole != Role.ADMIN && (actorId == null || !circle.getOrganizerId().equals(actorId))) {
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
          return new AttendeeDto(reg.getReaderId(), name, email, reg.getRegisteredAt());
        })
        .toList();
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
      return;
    }
    if (circle.getOrganizerId().equals(readerId)) {
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

  private RegistrationDto toRegistrationDto(EventRegistration reg, Event event) {
    String title = event != null ? event.getTitle() : "Unknown Event";
    UUID circleId = event != null ? event.getCircleId() : null;
    return new RegistrationDto(
        reg.getId(),
        reg.getEventId(),
        title,
        circleId,
        reg.getReaderId(),
        reg.getStatus(),
        reg.getRegisteredAt(),
        reg.getCancelledAt());
  }
}
