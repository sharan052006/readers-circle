package com.readerscircle.events;

import com.readerscircle.auth.Role;
import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.circles.Circle;
import com.readerscircle.circles.CircleRepository;
import com.readerscircle.events.dto.CreateEventRequest;
import com.readerscircle.events.dto.EventDetailDto;
import com.readerscircle.events.dto.EventSummaryDto;
import com.readerscircle.events.dto.UpdateEventRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventService {

  private final EventRepository eventRepository;
  private final EventRegistrationRepository registrationRepository;
  private final CircleRepository circleRepository;
  private final UserRepository userRepository;
  private final EventLifecycleService eventLifecycleService;
  private final Clock clock;

  public EventService(
      EventRepository eventRepository,
      EventRegistrationRepository registrationRepository,
      CircleRepository circleRepository,
      UserRepository userRepository,
      EventLifecycleService eventLifecycleService,
      Clock clock) {
    this.eventRepository = eventRepository;
    this.registrationRepository = registrationRepository;
    this.circleRepository = circleRepository;
    this.userRepository = userRepository;
    this.eventLifecycleService = eventLifecycleService;
    this.clock = clock;
  }

  @Transactional
  public EventDetailDto createEvent(UUID circleId, CreateEventRequest req, UUID actorId, Role actorRole) {
    Circle circle =
        circleRepository
            .findById(circleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));

    assertCircleOrganizer(circle, actorId, actorRole);

    if (!circle.isActive()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot create events for an inactive circle");
    }

    Event event = new Event(circleId, req.title());
    event.setDescription(req.description());
    event.setReadingTopic(req.readingTopic());
    event.setEventDate(req.eventDate());
    event.setEventTime(req.eventTime());
    event.setVenue(req.venue());
    event.setType(normalizeType(req.type()));
    event.setMode(normalizeMode(req.mode(), req.venue()));
    event.setLocation(normalizeLocation(req.location()));
    event.setCapacity(req.capacity());
    event.setRegistrationDeadline(req.registrationDeadline());
    event.setCoverImageUrl(req.coverImageUrl());
    event.setGuidelines(req.guidelines());

    if (req.publishNow()) {
      defaultDeadlineForPublish(event);
      try {
        event.publish();
      } catch (IllegalArgumentException | IllegalStateException e) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
      }
    }

    Event saved = eventRepository.save(event);
    return toDetailDto(saved, circle, actorId, true);
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
        actorRole == Role.ADMIN || isEffectiveOrganizer(circle, actorId);

    return toDetailDto(event, circle, actorId, isOrganizerOrAdmin);
  }

  @Transactional(readOnly = true)
  public List<EventSummaryDto> listEventsForCircle(UUID circleId, String scope, UUID actorId) {
    return listEventsForCircle(circleId, scope, null, null, null, actorId);
  }

  /**
   * Per-circle events for the circle detail Events tab: scope upcoming|past|all
   * plus text search (title/topic/venue), status filter and sort
   * (date_asc default for upcoming, date_desc, newest, oldest, name).
   */
  @Transactional(readOnly = true)
  public List<EventSummaryDto> listEventsForCircle(
      UUID circleId, String scope, String search, String status, String sort, UUID actorId) {
    return listEventsForCircle(circleId, scope, search, status, sort, null, null, actorId);
  }

  /**
   * Extended listing for the reader Circle Events screen: adds venue substring
   * and month (YYYY-MM) filters for the Location/Month dropdowns.
   */
  @Transactional(readOnly = true)
  public List<EventSummaryDto> listEventsForCircle(
      UUID circleId,
      String scope,
      String search,
      String status,
      String sort,
      String venue,
      String month,
      UUID actorId) {
    return listEventsForCircle(circleId, scope, search, status, sort, venue, month, null, null, actorId);
  }

  /**
   * Full listing for the reader Circle Events screen: adds event-type
   * multi-select and online/offline mode filters.
   */
  @Transactional(readOnly = true)
  public List<EventSummaryDto> listEventsForCircle(
      UUID circleId,
      String scope,
      String search,
      String status,
      String sort,
      String venue,
      String month,
      String types,
      String mode,
      UUID actorId) {
    Circle circle =
        circleRepository
            .findById(circleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));

    LocalDate today = LocalDate.now(clock);
    LocalTime nowTime = LocalTime.now(clock);
    List<Event> events;

    if ("past".equalsIgnoreCase(scope)) {
      events = eventRepository.findPastByCircleId(circleId, today, nowTime);
    } else if ("all".equalsIgnoreCase(scope)) {
      events = eventRepository.findByCircleIdOrderByCreatedAtDesc(circleId);
    } else {
      // Default: upcoming
      events = eventRepository.findUpcomingByCircleId(circleId, today, nowTime);
    }

    java.util.stream.Stream<Event> s = events.stream();
    if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status.trim())) {
      try {
        EventStatus want = EventStatus.valueOf(status.trim().toUpperCase());
        s = s.filter(e -> e.getStatus() == want);
      } catch (IllegalArgumentException ignored) {
      }
    }
    if (search != null && !search.isBlank()) {
      String q = search.trim().toLowerCase();
      s = s.filter(
          e ->
              (e.getTitle() != null && e.getTitle().toLowerCase().contains(q))
                  || (e.getReadingTopic() != null && e.getReadingTopic().toLowerCase().contains(q))
                  || (e.getVenue() != null && e.getVenue().toLowerCase().contains(q)));
    }
    if (venue != null && !venue.isBlank() && !"all".equalsIgnoreCase(venue.trim())) {
      String v = venue.trim().toLowerCase();
      s = s.filter(e -> e.getVenue() != null && e.getVenue().toLowerCase().contains(v));
    }
    if (month != null && month.trim().matches("\\d{4}-\\d{2}")) {
      String want = month.trim();
      s = s.filter(
          e -> e.getEventDate() != null && e.getEventDate().toString().startsWith(want));
    }
    if (types != null && !types.isBlank()) {
      java.util.Set<String> wanted = new java.util.HashSet<>();
      for (String part : types.split(",")) {
        if (!part.isBlank()) {
          wanted.add(part.trim().toUpperCase().replace(' ', '_'));
        }
      }
      if (!wanted.isEmpty()) {
        s = s.filter(e -> e.getType() != null && wanted.contains(e.getType()));
      }
    }
    if (mode != null && !mode.isBlank() && !"all".equalsIgnoreCase(mode.trim())) {
      String wantMode = mode.trim().toUpperCase();
      s = s.filter(
          e -> {
            String m = e.getMode() != null ? e.getMode() : normalizeMode(null, e.getVenue());
            return wantMode.equals(m);
          });
    }
    List<Event> filtered = new java.util.ArrayList<>(s.toList());
    String so = sort == null ? "" : sort.trim().toLowerCase();
    switch (so) {
      case "date_desc", "date-desc" -> filtered.sort(
          java.util.Comparator.comparing(Event::getEventDate,
              java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
      case "newest" -> filtered.sort(
          java.util.Comparator.comparing(Event::getCreatedAt,
              java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
      case "oldest" -> filtered.sort(
          java.util.Comparator.comparing(Event::getCreatedAt,
              java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
      case "name", "name_asc" -> filtered.sort(
          java.util.Comparator.comparing(e -> e.getTitle() == null ? "" : e.getTitle().toLowerCase()));
      default -> {
        if (!"all".equalsIgnoreCase(scope) && !"past".equalsIgnoreCase(scope)) {
          filtered.sort(
              java.util.Comparator.comparing(Event::getEventDate,
                  java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        }
      }
    }

    return filtered.stream().map(e -> toSummaryDto(e, circle.getName(), actorId)).toList();
  }

  @Transactional(readOnly = true)
  public List<EventSummaryDto> listAllEvents(UUID actorId, Role actorRole) {
    return listAllEvents(actorId, actorRole, null, null, null, null);
  }

  /**
   * All-circles event list backing the admin Events page: text search across
   * title/topic/venue/circle name, circle filter (All Circles dropdown),
   * status filter, and sort (date_desc = "Date Newest First", date_asc,
   * newest/oldest by creation, name). Default order stays creation-desc so
   * existing callers are unaffected.
   */
  @Transactional(readOnly = true)
  public List<EventSummaryDto> listAllEvents(
      UUID actorId, Role actorRole, String search, String circleId, String status, String sort) {
    return listAllEvents(actorId, actorRole, search, circleId, status, sort, null, null, null, null);
  }

  /**
   * Reader Events home: adds scope (upcoming|past|all), event-type
   * multi-select, mode (ONLINE/OFFLINE) and month (YYYY-MM) filters.
   */
  @Transactional(readOnly = true)
  public List<EventSummaryDto> listAllEvents(
      UUID actorId,
      Role actorRole,
      String search,
      String circleId,
      String status,
      String sort,
      String scope,
      String types,
      String mode,
      String month) {
    // Move any event whose scheduled date+time has passed into COMPLETED before
    // listing, so it leaves "Upcoming" and shows under "Past" for every role.
    eventLifecycleService.completeOverdueEvents();
    List<Event> events = eventRepository.findAllByOrderByCreatedAtDesc();
    Map<UUID, Circle> circleMap =
        circleRepository.findAll().stream()
            .collect(Collectors.toMap(Circle::getId, c -> c, (a, b) -> a));

    java.util.stream.Stream<Event> s = events.stream()
        .filter(
            e -> {
              if (actorRole == Role.ADMIN) return true;
              if (e.getStatus() != EventStatus.DRAFT) return true;
              Circle circle = circleMap.get(e.getCircleId());
              return circle != null && isEffectiveOrganizer(circle, actorId);
            });

    if (circleId != null && !circleId.isBlank() && !"all".equalsIgnoreCase(circleId.trim())) {
      String want = circleId.trim();
      s = s.filter(e -> e.getCircleId() != null && e.getCircleId().toString().equalsIgnoreCase(want));
    }
    if (scope != null && !scope.isBlank() && !"all".equalsIgnoreCase(scope.trim())) {
      LocalDate today = LocalDate.now(clock);
      LocalTime nowTime = LocalTime.now(clock);
      if ("past".equalsIgnoreCase(scope.trim())) {
        s = s.filter(
            e ->
                e.getStatus() == EventStatus.COMPLETED || isPastDateTime(e, today, nowTime));
      } else if ("upcoming".equalsIgnoreCase(scope.trim())) {
        s = s.filter(
            e ->
                e.getStatus() == EventStatus.PUBLISHED && !isPastDateTime(e, today, nowTime));
      }
    }
    if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status.trim())) {
      try {
        EventStatus want = EventStatus.valueOf(status.trim().toUpperCase());
        s = s.filter(e -> e.getStatus() == want);
      } catch (IllegalArgumentException ignored) {
      }
    }
    if (search != null && !search.isBlank()) {
      String q = search.trim().toLowerCase();
      s = s.filter(
          e -> {
            Circle circle = circleMap.get(e.getCircleId());
            String circleName = circle != null ? circle.getName() : "";
            String city = circle != null ? circle.getCity() : "";
            return (e.getTitle() != null && e.getTitle().toLowerCase().contains(q))
                || (e.getReadingTopic() != null && e.getReadingTopic().toLowerCase().contains(q))
                || (e.getVenue() != null && e.getVenue().toLowerCase().contains(q))
                || (e.getDescription() != null && e.getDescription().toLowerCase().contains(q))
                || (circleName.toLowerCase().contains(q))
                || (city.toLowerCase().contains(q));
          });
    }
    if (types != null && !types.isBlank()) {
      java.util.Set<String> wanted = new java.util.HashSet<>();
      for (String part : types.split(",")) {
        if (!part.isBlank()) {
          wanted.add(part.trim().toUpperCase().replace(' ', '_'));
        }
      }
      if (!wanted.isEmpty()) {
        s = s.filter(e -> e.getType() != null && wanted.contains(e.getType()));
      }
    }
    if (mode != null && !mode.isBlank() && !"all".equalsIgnoreCase(mode.trim())) {
      String wantMode = mode.trim().toUpperCase();
      s = s.filter(
          e -> {
            String m = e.getMode() != null ? e.getMode() : normalizeMode(null, e.getVenue());
            return wantMode.equals(m);
          });
    }
    if (month != null && month.trim().matches("\\d{4}-\\d{2}")) {
      String wantMonth = month.trim();
      s = s.filter(
          e -> e.getEventDate() != null && e.getEventDate().toString().startsWith(wantMonth));
    }

    List<Event> filtered = new java.util.ArrayList<>(s.toList());
    String so = sort == null ? "" : sort.trim().toLowerCase();
    switch (so) {
      case "date_desc", "date-desc", "newest_first", "date_newest" -> filtered.sort(
          java.util.Comparator.comparing(Event::getEventDate,
              java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
      case "date_asc", "date-asc", "oldest_first" -> filtered.sort(
          java.util.Comparator.comparing(Event::getEventDate,
              java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
      case "newest" -> filtered.sort(
          java.util.Comparator.comparing(Event::getCreatedAt,
              java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
      case "oldest" -> filtered.sort(
          java.util.Comparator.comparing(Event::getCreatedAt,
              java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
      case "name", "name_asc" -> filtered.sort(
          java.util.Comparator.comparing(e -> e.getTitle() == null ? "" : e.getTitle().toLowerCase()));
      default -> { /* keep repository order (creation desc) */ }
    }

    return filtered.stream()
        .map(
            e -> {
              Circle circle = circleMap.get(e.getCircleId());
              String circleName = circle != null ? circle.getName() : "Unknown Circle";
              return toSummaryDto(e, circleName, actorId);
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public List<EventSummaryDto> listAllEvents(UUID actorId) {
    return listAllEvents(actorId, Role.ADMIN);
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

    assertCircleOrganizer(circle, actorId, actorRole);

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
    if (req.type() != null) {
      event.setType(req.type().isBlank() ? null : normalizeType(req.type()));
    }
    if (req.mode() != null) {
      event.setMode(req.mode().isBlank() ? null : normalizeMode(req.mode(), event.getVenue()));
    }
    if (req.location() != null) {
      event.setLocation(normalizeLocation(req.location()));
    }
    if (req.registrationDeadline() != null) {
      event.setRegistrationDeadline(req.registrationDeadline());
    }
    if (req.coverImageUrl() != null) {
      event.setCoverImageUrl(req.coverImageUrl());
    }
    if (req.guidelines() != null) {
      event.setGuidelines(req.guidelines());
    }

    if (req.status() != null && req.status() != event.getStatus()) {
      if (req.status() == EventStatus.PUBLISHED) {
        defaultDeadlineForPublish(event);
      }
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
    return toDetailDto(saved, circle, actorId, true);
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

    assertCircleOrganizer(circle, actorId, actorRole);

    try {
      event.cancel();
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    Event saved = eventRepository.save(event);
    return toDetailDto(saved, circle, actorId, true);
  }

  private void assertCircleOrganizer(Circle circle, UUID actorId, Role actorRole) {
    // Admins manage every circle's events (admin Events UI creates/edits for any circle).
    if (actorRole == Role.ADMIN) {
      if (actorId == null) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
      }
      return;
    }
    if (actorId == null || !isEffectiveOrganizer(circle, actorId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Only the appointed organizer of this reading circle can create or manage its events");
    }
  }

  /**
   * Effective organizer = assigned as the circle's organizer AND still holding
   * a live ORGANIZER/ADMIN role. Role is read from the DB (not the JWT) so a
   * demotion takes effect immediately, even for tokens minted before it.
   */
  private boolean isEffectiveOrganizer(Circle circle, UUID actorId) {
    if (circle == null || actorId == null || !actorId.equals(circle.getOrganizerId())) {
      return false;
    }
    Role role = userRepository.findById(actorId).map(User::getRole).orElse(null);
    return role == Role.ORGANIZER || role == Role.ADMIN;
  }

  /** Backwards-compatible check without role (non-admin path). */
  private void assertCircleOrganizer(Circle circle, UUID actorId) {
    assertCircleOrganizer(circle, actorId, null);
  }

  /**
   * Datetime-aware past check shared by the in-memory scope filters: an event
   * is past once its date+time passes. Null date never counts as past; null
   * time counts till end of day (same convention as the publish deadline).
   */
  static boolean isPastDateTime(Event event, LocalDate today, LocalTime nowTime) {
    if (event.getEventDate() == null) {
      return false;
    }
    if (event.getEventDate().isBefore(today)) {
      return true;
    }
    if (!event.getEventDate().isEqual(today)) {
      return false;
    }
    LocalTime time = event.getEventTime() != null ? event.getEventTime() : LocalTime.of(23, 59);
    return time.isBefore(nowTime);
  }

  /**
   * The create/edit UI has no deadline input: when publishing without an
   * explicit deadline, registration stays open until the event starts.
   */
  private void defaultDeadlineForPublish(Event event) {
    if (event.getRegistrationDeadline() != null) {
      return;
    }
    if (event.getEventDate() == null) {
      return;
    }
    LocalTime time = event.getEventTime() != null ? event.getEventTime() : LocalTime.of(23, 59);
    event.setRegistrationDeadline(
        event.getEventDate().atTime(time).atZone(ZoneId.systemDefault()).toInstant());
  }

  private EventSummaryDto toSummaryDto(Event event, String circleName, UUID actorId) {
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
        circleName,
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
        event.getCreatedAt(),
        event.getType(),
        event.getMode(),
        event.getLocation());
  }

  private EventDetailDto toDetailDto(
      Event event, Circle circle, UUID actorId, boolean isOrganizerOrAdmin) {
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
        circle.getName(),
        circle.getOrganizerId(),
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
        event.getCreatedAt(),
        event.getType(),
        event.getMode(),
        event.getGuidelines(),
        event.getLocation());
  }

  static final java.util.Set<String> EVENT_TYPES =
      java.util.Set.of("DISCUSSION", "WORKSHOP", "READING", "OPEN_SESSION", "MEETUP");
  static final java.util.Set<String> EVENT_MODES = java.util.Set.of("ONLINE", "OFFLINE");

  static String normalizeType(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String v = raw.trim().toUpperCase().replace(' ', '_').replace('-', '_');
    if ("OPEN-SESSION".equals(v) || "OPENSESSION".equals(v)) {
      v = "OPEN_SESSION";
    }
    if (!EVENT_TYPES.contains(v)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Unknown event type: " + raw.trim());
    }
    return v;
  }

  static String normalizeLocation(String raw) {
    if (raw == null) {
      return null;
    }
    String v = raw.trim();
    return v.isEmpty() ? null : v;
  }

  static String normalizeMode(String raw, String venue) {
    if (raw == null || raw.isBlank()) {
      // Backfill for older events: infer from venue text.
      if (venue != null && venue.toLowerCase().contains("online")) {
        return "ONLINE";
      }
      return null;
    }
    String v = raw.trim().toUpperCase();
    if (!EVENT_MODES.contains(v)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Unknown event mode (use ONLINE or OFFLINE)");
    }
    return v;
  }
}
