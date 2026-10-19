package com.readerscircle.circles;

import com.readerscircle.auth.Role;
import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.circles.dto.CircleDetailDto;
import com.readerscircle.circles.dto.CircleSummaryDto;
import com.readerscircle.circles.dto.CreateCircleRequest;
import com.readerscircle.circles.dto.UpdateCircleRequest;
import com.readerscircle.events.EventRepository;
import com.readerscircle.events.EventRegistrationRepository;
import com.readerscircle.events.RegistrationStatus;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CircleService {

  private final CircleRepository circleRepository;
  private final MembershipRepository membershipRepository;
  private final UserRepository userRepository;
  private final EventRepository eventRepository;
  private final EventRegistrationRepository registrationRepository;
  private final CircleOrganizerRepository organizerRepository;

  @Autowired
  public CircleService(
      CircleRepository circleRepository,
      MembershipRepository membershipRepository,
      UserRepository userRepository,
      EventRepository eventRepository,
      EventRegistrationRepository registrationRepository,
      CircleOrganizerRepository organizerRepository) {
    this.circleRepository = circleRepository;
    this.membershipRepository = membershipRepository;
    this.userRepository = userRepository;
    this.eventRepository = eventRepository;
    this.registrationRepository = registrationRepository;
    this.organizerRepository = organizerRepository;
  }

  /** Constructor used by unit tests wiring 3 deps. */
  public CircleService(
      CircleRepository circleRepository,
      MembershipRepository membershipRepository,
      UserRepository userRepository) {
    this(circleRepository, membershipRepository, userRepository, null, null, null);
  }

  @Transactional(readOnly = true)
  public List<CircleSummaryDto> listCircles(String city, boolean isAdmin) {
    return listCircles(city, null, null, null, isAdmin);
  }

  /**
   * Circles list backing the admin Circles page: text search across
   * name/description/city/category/tags, status filter (admin only — readers always
   * see ACTIVE), and sort (name_asc default, name_desc, newest, oldest,
   * members_desc).
   */
  @Transactional(readOnly = true)
  public List<CircleSummaryDto> listCircles(
      String city, String search, String status, String sort, boolean isAdmin) {
    return listCircles(city, search, status, sort, null, null, null, isAdmin);
  }

  /**
   * Reader Explore tab: adds multi-category OR filter (matches category or any
   * tag) plus member-count range. All params optional.
   */
  @Transactional(readOnly = true)
  public List<CircleSummaryDto> listCircles(
      String city,
      String search,
      String status,
      String sort,
      String categories,
      Long minMembers,
      Long maxMembers,
      boolean isAdmin) {
    List<Circle> circles;
    if (city != null && !city.isBlank()) {
      if (isAdmin) {
        circles =
            circleRepository.findAll().stream()
                .filter(c -> c.getCity() != null && c.getCity().equalsIgnoreCase(city.trim()))
                .toList();
      } else {
        circles = circleRepository.findByCityIgnoreCaseAndStatus(city.trim(), CircleStatus.ACTIVE);
      }
    } else {
      circles = isAdmin ? circleRepository.findAll() : circleRepository.findAllByStatus(CircleStatus.ACTIVE);
    }

    if (isAdmin && status != null && !status.isBlank() && !"all".equalsIgnoreCase(status.trim())) {
      try {
        CircleStatus want = CircleStatus.valueOf(status.trim().toUpperCase());
        circles = circles.stream().filter(c -> c.getStatus() == want).toList();
      } catch (IllegalArgumentException ignored) {
      }
    }

    if (search != null && !search.isBlank()) {
      String q = search.trim().toLowerCase();
      circles =
          circles.stream()
              .filter(
                  c ->
                      contains(c.getName(), q)
                          || contains(c.getDescription(), q)
                          || contains(c.getCity(), q)
                          || contains(c.getCategory(), q)
                          || contains(c.getTags(), q))
              .toList();
    }

    if (categories != null && !categories.isBlank()) {
      java.util.Set<String> wanted = new java.util.HashSet<>();
      for (String part : categories.split(",")) {
        if (!part.isBlank()) {
          wanted.add(part.trim().toLowerCase());
        }
      }
      if (!wanted.isEmpty()) {
        circles =
            circles.stream()
                .filter(
                    c -> {
                      if (c.getCategory() != null
                          && wanted.contains(c.getCategory().trim().toLowerCase())) {
                        return true;
                      }
                      for (String t : splitTags(c.getTags())) {
                        if (wanted.contains(t.toLowerCase())) {
                          return true;
                        }
                      }
                      return false;
                    })
                .toList();
      }
    }

    circles = sortCircles(circles, sort);
    if (!isAdmin) {
      // Hidden circles (Organisers Lounge) never leak to readers/organizers via lists.
      circles = circles.stream().filter(c -> !c.isHidden()).toList();
    }
    List<CircleSummaryDto> out = circles.stream().map(this::toSummaryDto).toList();
    if (minMembers != null || maxMembers != null) {
      out =
          out.stream()
              .filter(
                  d ->
                      (minMembers == null || d.memberCount() >= minMembers)
                          && (maxMembers == null || d.memberCount() <= maxMembers))
              .toList();
    }
    return out;
  }

  /**
   * "Recommended for You" rail: circles sharing the reader's city or the
   * categories/tags of circles they already joined, excluding anything
   * joined, requested, organized, hidden or inactive. Ordered by overlap then
   * member count.
   */
  @Transactional(readOnly = true)
  public List<CircleSummaryDto> recommendedFor(UUID readerId, int limit) {
    int lim = Math.max(1, Math.min(12, limit));
    User me = userRepository.findById(readerId).orElse(null);
    java.util.Set<String> myCategories = new java.util.HashSet<>();
    java.util.Set<UUID> exclude = new java.util.HashSet<>();
    for (Membership m : membershipRepository.findByReaderId(readerId)) {
      exclude.add(m.getCircleId());
      circleRepository
          .findById(m.getCircleId())
          .ifPresent(
              c -> {
                if (c.getCategory() != null) {
                  myCategories.add(c.getCategory().trim().toLowerCase());
                }
                myCategories.addAll(
                    splitTags(c.getTags()).stream().map(t -> t.toLowerCase()).toList());
              });
    }
    circleRepository.findByOrganizerId(readerId).forEach(c -> exclude.add(c.getId()));
    String myCity = me != null && me.getCity() != null ? me.getCity().trim().toLowerCase() : null;

    record Scored(CircleSummaryDto dto, int score) {}
    List<Scored> scored = new java.util.ArrayList<>();
    for (Circle c : circleRepository.findAllByStatus(CircleStatus.ACTIVE)) {
      if (c.isHidden() || exclude.contains(c.getId())) {
        continue;
      }
      int score = 0;
      if (c.getCategory() != null && myCategories.contains(c.getCategory().trim().toLowerCase())) {
        score += 3;
      }
      for (String t : splitTags(c.getTags())) {
        if (myCategories.contains(t.toLowerCase())) {
          score += 2;
        }
      }
      if (myCity != null && c.getCity() != null && c.getCity().trim().toLowerCase().equals(myCity)) {
        score += 2;
      }
      scored.add(new Scored(toSummaryDto(c), score));
    }
    scored.sort(
        java.util.Comparator.comparingInt(Scored::score)
            .reversed()
            .thenComparing((a, b) -> Long.compare(b.dto().memberCount(), a.dto().memberCount())));
    return scored.stream().limit(lim).map(Scored::dto).toList();
  }

  @Transactional(readOnly = true)
  public CircleDetailDto getCircle(UUID id, boolean isAdmin) {
    Circle circle =
        circleRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));

    if (!circle.isActive() && !isAdmin) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found");
    }
    if (circle.isHidden() && !isAdmin) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found");
    }

    return toDetailDto(circle);
  }

  /**
   * Single aggregate for the circle detail header + Overview tab mini-cards:
   * member/event/upcoming/pending counts plus next upcoming events.
   */
  @Transactional(readOnly = true)
  public Map<String, Object> getCircleOverview(UUID id, boolean isAdmin) {
    Circle circle =
        circleRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));
    if (!circle.isActive() && !isAdmin) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found");
    }
    if (circle.isHidden() && !isAdmin) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found");
    }

    long members = memberCount(circle);
    long totalEvents =
        eventRepository != null ? eventRepository.findByCircleIdOrderByCreatedAtDesc(id).size() : 0;
    LocalDate today = LocalDate.now(java.time.ZoneId.systemDefault());
    java.time.LocalTime nowTime = java.time.LocalTime.now(java.time.ZoneId.systemDefault());
    long upcoming =
        eventRepository != null ? eventRepository.findUpcomingByCircleId(id, today, nowTime).size() : 0;
    long pending = membershipRepository.countByCircleIdAndStatus(id, MembershipStatus.PENDING);

    Map<String, Object> counts = new LinkedHashMap<>();
    counts.put("members", members);
    counts.put("totalEvents", totalEvents);
    counts.put("upcomingEvents", upcoming);
    counts.put("pendingRequests", pending);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("circle", toDetailDto(circle));
    out.put("counts", counts);
    return out;
  }

  @Transactional(readOnly = true)
  public List<CircleSummaryDto> getCirclesByOrganizer(UUID organizerId) {
    // Demotion safety: a demoted (READER) user keeps the organizer_id row until
    // an admin reassigns the circle, but must lose organizer powers immediately.
    if (!hasOrganizerRole(organizerId)) {
      return List.of();
    }
    return circleRepository.findByOrganizerId(organizerId).stream()
        .map(this::toSummaryDto)
        .toList();
  }

  /**
   * Effective organizer = assigned as the circle's organizer AND still holding
   * a live ORGANIZER/ADMIN role. Role is read from the DB (not the JWT) so a
   * demotion takes effect immediately, even for tokens minted before it.
   */
  @Transactional(readOnly = true)
  public boolean isEffectiveOrganizer(UUID circleId, UUID userId) {
    if (circleId == null || userId == null) {
      return false;
    }
    Circle circle = circleRepository.findById(circleId).orElse(null);
    return isEffectiveOrganizer(circle, userId);
  }

  /** Assignment + live-role check without an extra circle lookup. */
  @Transactional(readOnly = true)
  public boolean isEffectiveOrganizer(Circle circle, UUID userId) {
    if (circle == null || userId == null || !userId.equals(circle.getOrganizerId())) {
      return false;
    }
    return hasOrganizerRole(userId);
  }

  /** Live DB role check (JWT claims can predate a demotion/promotion). */
  @Transactional(readOnly = true)
  public boolean hasOrganizerRole(UUID userId) {
    if (userId == null) {
      return false;
    }
    Role role = userRepository.findById(userId).map(User::getRole).orElse(null);
    return role == Role.ORGANIZER || role == Role.ADMIN;
  }

  @Transactional
  public CircleDetailDto createCircle(CreateCircleRequest req) {
    User organizer =
        userRepository
            .findById(req.organizerId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organizer user not found"));

    if (organizer.getRole() != Role.ORGANIZER && organizer.getRole() != Role.ADMIN) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Appointed user must have ORGANIZER or ADMIN role");
    }
    if (!organizer.isActive()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointed organizer is deactivated");
    }

    String category = req.category() != null && !req.category().isBlank() ? req.category().trim() : null;
    String coverImageUrl =
        req.coverImageUrl() != null && !req.coverImageUrl().isBlank()
            ? req.coverImageUrl().trim()
            : null;

    Circle circle =
        new Circle(req.name().trim(), req.city().trim(), req.description().trim(), organizer.getId(), coverImageUrl, category);
    circle.setTags(joinTags(req.tags()));
    if (req.status() != null) {
      circle.setStatus(req.status());
    }
    Circle saved = circleRepository.save(circle);
    ensureOrganizerRow(saved.getId(), organizer.getId());

    return toDetailDto(saved);
  }

  @Transactional
  public CircleDetailDto updateCircle(UUID id, UpdateCircleRequest req) {
    Circle circle =
        circleRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));

    if (req.name() != null && !req.name().isBlank()) {
      if (req.name().trim().length() > 50) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Circle name must be at most 50 characters");
      }
      circle.setName(req.name().trim());
    }
    if (req.city() != null && !req.city().isBlank()) {
      circle.setCity(req.city().trim());
    }
    if (req.description() != null && !req.description().isBlank()) {
      if (req.description().trim().length() > 300) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description must be at most 300 characters");
      }
      circle.setDescription(req.description().trim());
    }
    if (req.organizerId() != null) {
      User organizer =
          userRepository
              .findById(req.organizerId())
              .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organizer user not found"));
      if (organizer.getRole() != Role.ORGANIZER && organizer.getRole() != Role.ADMIN) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Appointed user must have ORGANIZER or ADMIN role");
      }
      if (!req.organizerId().equals(circle.getOrganizerId())) {
        retireOrganizerRow(id, circle.getOrganizerId());
        circle.setOrganizerId(req.organizerId());
        ensureOrganizerRow(id, req.organizerId());
      }
    }
    if (req.coverImageUrl() != null) {
      circle.setCoverImageUrl(req.coverImageUrl().isBlank() ? null : req.coverImageUrl().trim());
    }
    if (req.category() != null) {
      circle.setCategory(req.category().isBlank() ? null : req.category().trim());
    }
    if (req.tags() != null) {
      circle.setTags(joinTags(req.tags()));
    }
    if (req.status() != null) {
      circle.setStatus(req.status());
    } else if (req.active() != null) {
      circle.setStatus(req.active() ? CircleStatus.ACTIVE : CircleStatus.INACTIVE);
    }

    Circle saved = circleRepository.save(circle);
    return toDetailDto(saved);
  }

  @Transactional
  public CircleDetailDto setCoverImageUrl(UUID id, String coverImageUrl) {
    Circle circle =
        circleRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));
    circle.setCoverImageUrl(coverImageUrl);
    return toDetailDto(circleRepository.save(circle));
  }

  @Transactional
  public void deactivateCircle(UUID id) {
    Circle circle =
        circleRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circle not found"));
    circle.setStatus(CircleStatus.INACTIVE);
    circleRepository.save(circle);
  }

  private long memberCount(Circle circle) {
    long approvedReaders =
        membershipRepository.countByCircleIdAndStatusAndReaderIdNot(
            circle.getId(), MembershipStatus.APPROVED, circle.getOrganizerId());
    return circle.getOrganizerId() != null ? approvedReaders + 1 : approvedReaders;
  }

  private List<Circle> sortCircles(List<Circle> circles, String sort) {
    String s = sort == null ? "" : sort.trim().toLowerCase();
    return switch (s) {
      case "name_desc", "name-desc", "z-a" -> circles.stream()
          .sorted(Comparator.comparing(c -> nullSafe(c.getName()).toLowerCase(), Comparator.reverseOrder()))
          .toList();
      case "newest" -> circles.stream()
          .sorted(Comparator.comparing(Circle::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
          .toList();
      case "oldest" -> circles.stream()
          .sorted(Comparator.comparing(Circle::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
          .toList();
      case "members_desc", "members" -> circles.stream()
          .sorted(Comparator.comparingLong(this::memberCount).reversed())
          .toList();
      default -> circles.stream()
          .sorted(Comparator.comparing(c -> nullSafe(c.getName()).toLowerCase()))
          .toList();
    };
  }

  private String nullSafe(String v) {
    return v == null ? "" : v;
  }

  private boolean contains(String haystack, String q) {
    return haystack != null && haystack.toLowerCase().contains(q);
  }

  private CircleSummaryDto toSummaryDto(Circle circle) {
    long memberCount = memberCount(circle);
    return new CircleSummaryDto(
        circle.getId(),
        circle.getName(),
        circle.getCity(),
        circle.getDescription(),
        circle.getOrganizerId(),
        circle.getStatus(),
        circle.getCreatedAt(),
        memberCount,
        circle.getCoverImageUrl(),
        circle.getCategory(),
        splitTags(circle.getTags()),
        circle.isHidden());
  }

  private CircleDetailDto toDetailDto(Circle circle) {
    String organizerName =
        userRepository.findById(circle.getOrganizerId()).map(User::getName).orElse("Unknown");
    long memberCount = memberCount(circle);
    return new CircleDetailDto(
        circle.getId(),
        circle.getName(),
        circle.getCity(),
        circle.getDescription(),
        circle.getOrganizerId(),
        organizerName,
        circle.getStatus(),
        circle.getCreatedAt(),
        memberCount,
        circle.getCoverImageUrl(),
        circle.getCategory(),
        splitTags(circle.getTags()),
        circle.isHidden());
  }

  /** Current primary organizer always has a current roster row (idempotent). */
  @Transactional
  public void ensureOrganizerRow(UUID circleId, UUID userId) {
    if (organizerRepository == null || circleId == null || userId == null) {
      return;
    }
    organizerRepository
        .findByCircleIdAndUserId(circleId, userId)
        .ifPresentOrElse(
            row -> {
              if (!row.isCurrent()) {
                row.reinstate();
                organizerRepository.save(row);
              }
            },
            () -> organizerRepository.save(new CircleOrganizer(circleId, userId)));
  }

  private void retireOrganizerRow(UUID circleId, UUID userId) {
    if (organizerRepository == null || circleId == null || userId == null) {
      return;
    }
    organizerRepository
        .findByCircleIdAndUserId(circleId, userId)
        .ifPresent(
            row -> {
              if (row.isCurrent()) {
                row.markRemoved();
                organizerRepository.save(row);
              }
            });
  }

  static List<String> splitTags(String tags) {
    if (tags == null || tags.isBlank()) {
      return List.of();
    }
    List<String> out = new java.util.ArrayList<>();
    for (String part : tags.split(",")) {
      if (!part.isBlank() && out.size() < 20) {
        out.add(part.trim());
      }
    }
    return out;
  }

  static String joinTags(java.util.List<String> tags) {
    if (tags == null) {
      return null;
    }
    List<String> clean = new java.util.ArrayList<>();
    for (String t : tags) {
      if (t != null && !t.isBlank() && clean.size() < 20) {
        clean.add(t.trim());
      }
    }
    return clean.isEmpty() ? null : String.join(",", clean);
  }
}
