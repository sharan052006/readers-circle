package com.readerscircle.events;

import com.readerscircle.auth.Role;
import com.readerscircle.circles.Circle;
import com.readerscircle.circles.CircleRepository;
import com.readerscircle.events.dto.CreateEventRequest;
import com.readerscircle.events.dto.EventDetailDto;
import com.readerscircle.events.dto.EventSummaryDto;
import com.readerscircle.events.dto.UpdateEventRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventService {

  private final EventRepository eventRepository;
  private final EventRegistrationRepository registrationRepository;
  private final CircleRepository circleRepository;
  private final Clock clock;

  public EventService(
      EventRepository eventRepository,
      EventRegistrationRepository registrationRepository,
      CircleRepository circleRepository,
      Clock clock) {
    this.eventRepository = eventRepository;
    this.registrationRepository = registrationRepository;
    this.circleRepository = circleRepository;
    this.clock = clock;
  }

  @Transactional
  public EventDetailDto createEvent(UUID circleId, CreateEventRequest req, UUID actorId, Role actorRole) {
    Circle circle =
        circleRepository
            .findById(circleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));

    assertOrganizerOrAdmin(circle, actorId, actorRole);

    if (!circle.isActive()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot create events for an inactive circle");
    }

    Event event = new Event(circleId, req.title());
    event.setDescription(req.description());
    event.setReadingTopic(req.readingTopic());
    event.setEventDate(req.eventDate());
    event.setEventTime(req.eventTime());
    event.setVenue(req.venue());
    event.setCapacity(req.capacity());
    event.setRegistrationDeadline(req.registrationDeadline());
    event.setCoverImageUrl(req.coverImageUrl());

    if (req.publishNow()) {
      try {
        event.publish();
      } catch (IllegalArgumentException | IllegalStateException e) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
      }
    }

    Event saved = eventRepository.save(event);
    return toDetailDto(saved, circle.getName(), actorId, true);
  }

  @Transactional(readOnly = true)
  public EventDetailDto getEvent(UUID eventId, UUID actorId, Role actorRole) {
    Event event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    Circle circle =
        circleRepository
            .findById(event.getCircleId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));

    boolean isOrganizerOrAdmin =
        actorRole == Role.ADMIN || (actorId != null && circle.getOrganizerId().equals(actorId));

    return toDetailDto(event, circle.getName(), actorId, isOrganizerOrAdmin);
  }

  @Transactional(readOnly = true)
  public List<EventSummaryDto> listEventsForCircle(UUID circleId, String scope, UUID actorId) {
    if (!circleRepository.existsById(circleId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found");
    }

    LocalDate today = LocalDate.now(clock);
    List<Event> events;

    if ("past".equalsIgnoreCase(scope)) {
      events = eventRepository.findPastByCircleId(circleId, today);
    } else if ("all".equalsIgnoreCase(scope)) {
      events = eventRepository.findByCircleIdOrderByCreatedAtDesc(circleId);
    } else {
      // Default: upcoming
      events = eventRepository.findUpcomingByCircleId(circleId, today);
    }

    return events.stream().map(e -> toSummaryDto(e, actorId)).toList();
  }

  @Transactional
  public EventDetailDto updateEvent(UUID eventId, UpdateEventRequest req, UUID actorId, Role actorRole) {
    Event event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    Circle circle =
        circleRepository
            .findById(event.getCircleId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));

    assertOrganizerOrAdmin(circle, actorId, actorRole);

    if (event.getStatus() == EventStatus.COMPLETED || event.getStatus() == EventStatus.CANCELLED) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "Cannot update an event in terminal state: " + event.getStatus());
    }

    if (req.capacity() != null) {
      long activeCount =
          registrationRepository.countByEventIdAndStatus(eventId, RegistrationStatus.REGISTERED);
      if (req.capacity() < activeCount) {
        throw new ResponseStatusException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Cannot reduce capacity below current registered count: " + activeCount);
      }
      event.setCapacity(req.capacity());
    }

    if (req.title() != null && !req.title().isBlank()) {
      event.setTitle(req.title().trim());
    }
    if (req.description() != null) {
      event.setDescription(req.description());
    }
    if (req.readingTopic() != null) {
      event.setReadingTopic(req.readingTopic());
    }
    if (req.eventDate() != null) {
      event.setEventDate(req.eventDate());
    }
    if (req.eventTime() != null) {
      event.setEventTime(req.eventTime());
    }
    if (req.venue() != null) {
      event.setVenue(req.venue());
    }
    if (req.registrationDeadline() != null) {
      event.setRegistrationDeadline(req.registrationDeadline());
    }
    if (req.coverImageUrl() != null) {
      event.setCoverImageUrl(req.coverImageUrl());
    }

    if (req.status() != null && req.status() != event.getStatus()) {
      try {
        switch (req.status()) {
          case PUBLISHED -> event.publish();
          case COMPLETED -> event.complete();
          case CANCELLED -> event.cancel();
          default -> throw new ResponseStatusException(
              HttpStatus.UNPROCESSABLE_ENTITY, "Invalid state transition to " + req.status());
        }
      } catch (IllegalArgumentException | IllegalStateException e) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
      }
    }

    Event saved = eventRepository.save(event);
    return toDetailDto(saved, circle.getName(), actorId, true);
  }

  @Transactional
  public EventDetailDto cancelEvent(UUID eventId, UUID actorId, Role actorRole) {
    Event event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    Circle circle =
        circleRepository
            .findById(event.getCircleId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));

    assertOrganizerOrAdmin(circle, actorId, actorRole);

    try {
      event.cancel();
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    Event saved = eventRepository.save(event);
    return toDetailDto(saved, circle.getName(), actorId, true);
  }

  private void assertOrganizerOrAdmin(Circle circle, UUID actorId, Role actorRole) {
    if (actorRole == Role.ADMIN) {
      return;
    }
    if (actorId == null || !circle.getOrganizerId().equals(actorId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only circle organizer or admin can perform this action");
    }
  }

  private EventSummaryDto toSummaryDto(Event event, UUID actorId) {
    long count =
        registrationRepository.countByEventIdAndStatus(event.getId(), RegistrationStatus.REGISTERED);
    boolean isReg = false;
    if (actorId != null) {
      isReg =
          registrationRepository
              .findByEventIdAndReaderId(event.getId(), actorId)
              .map(r -> r.getStatus() == RegistrationStatus.REGISTERED)
              .orElse(false);
    }
    return new EventSummaryDto(
        event.getId(),
        event.getCircleId(),
        event.getTitle(),
        event.getReadingTopic(),
        event.getEventDate(),
        event.getEventTime(),
        event.getVenue(),
        event.getCapacity(),
        count,
        event.getRegistrationDeadline(),
        event.getCoverImageUrl(),
        event.getStatus(),
        isReg,
        event.getCreatedAt());
  }

  private EventDetailDto toDetailDto(
      Event event, String circleName, UUID actorId, boolean isOrganizerOrAdmin) {
    long count =
        registrationRepository.countByEventIdAndStatus(event.getId(), RegistrationStatus.REGISTERED);
    boolean isReg = false;
    if (actorId != null) {
      isReg =
          registrationRepository
              .findByEventIdAndReaderId(event.getId(), actorId)
              .map(r -> r.getStatus() == RegistrationStatus.REGISTERED)
              .orElse(false);
    }
    return new EventDetailDto(
        event.getId(),
        event.getCircleId(),
        circleName,
        event.getTitle(),
        event.getDescription(),
        event.getReadingTopic(),
        event.getEventDate(),
        event.getEventTime(),
        event.getVenue(),
        event.getCapacity(),
        count,
        event.getRegistrationDeadline(),
        event.getCoverImageUrl(),
        event.getStatus(),
        isReg,
        isOrganizerOrAdmin,
        event.getCreatedAt());
  }
}
