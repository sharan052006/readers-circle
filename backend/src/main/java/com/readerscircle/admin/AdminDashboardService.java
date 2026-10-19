package com.readerscircle.admin;

import com.readerscircle.auth.Role;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.circles.Circle;
import com.readerscircle.circles.CircleRepository;
import com.readerscircle.circles.CircleStatus;
import com.readerscircle.circles.Membership;
import com.readerscircle.circles.MembershipRepository;
import com.readerscircle.circles.MembershipStatus;
import com.readerscircle.discussions.Discussion;
import com.readerscircle.discussions.DiscussionRepository;
import com.readerscircle.events.Event;
import com.readerscircle.events.EventRegistrationRepository;
import com.readerscircle.events.EventRepository;
import com.readerscircle.events.RegistrationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Powers the Admin Dashboard: top stat cards, top circles, upcoming events
 * (all circles), platform overview (last 7 days), cumulative growth timeseries,
 * circle distribution and the recent-activity feed. All values are computed
 * from real tables.
 */
@Service
@Transactional(readOnly = true)
public class AdminDashboardService {

  private final UserRepository users;
  private final CircleRepository circles;
  private final EventRepository events;
  private final EventRegistrationRepository registrations;
  private final MembershipRepository memberships;
  private final DiscussionRepository discussions;

  public AdminDashboardService(
      UserRepository users,
      CircleRepository circles,
      EventRepository events,
      EventRegistrationRepository registrations,
      MembershipRepository memberships,
      DiscussionRepository discussions) {
    this.users = users;
    this.circles = circles;
    this.events = events;
    this.registrations = registrations;
    this.memberships = memberships;
    this.discussions = discussions;
  }

  public Map<String, Object> dashboard(String range) {
    Map<String, Object> data = new LinkedHashMap<>();

    OffsetDateTime nowOdt = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime weekAgoOdt = nowOdt.minusDays(7);
    OffsetDateTime twoWeeksAgoOdt = nowOdt.minusDays(14);
    Instant weekAgo = weekAgoOdt.toInstant();
    Instant twoWeeksAgo = twoWeeksAgoOdt.toInstant();

    // Hidden circles (Organisers Lounge) are chat groups, not circles: they are
    // excluded from every circle metric on the dashboard.
    List<Circle> visible = visibleCircles();

    long totalCircles = visible.size();
    long totalEvents = events.count();
    long totalMembers = users.countByRoleIn(List.of(Role.READER, Role.ORGANIZER));
    long totalDiscussions = discussions.count();

    long circlesTrend =
        visible.stream().filter(c -> c.getCreatedAt() != null && c.getCreatedAt().isAfter(weekAgoOdt)).count();
    long eventsTrend = events.countByCreatedAtAfter(weekAgo);
    long membersTrend = users.countByRoleInAndCreatedAtAfter(List.of(Role.READER, Role.ORGANIZER), weekAgoOdt);
    long discussionsTrend = discussions.countByCreatedAtAfter(weekAgo);

    long circlesPrev =
        visible.stream().filter(c -> c.getCreatedAt() != null && c.getCreatedAt().isAfter(twoWeeksAgoOdt)).count()
            - circlesTrend;
    long eventsPrev = events.countByCreatedAtAfter(twoWeeksAgo) - eventsTrend;
    long membersPrev =
        users.countByRoleInAndCreatedAtAfter(List.of(Role.READER, Role.ORGANIZER), twoWeeksAgoOdt) - membersTrend;
    long discussionsPrev = discussions.countByCreatedAtAfter(twoWeeksAgo) - discussionsTrend;

    Map<String, Object> stats = new LinkedHashMap<>();
    stats.put("totalCircles", totalCircles);
    stats.put("circlesTrend", circlesTrend);
    stats.put("circlesChangePct", pct(circlesTrend, circlesPrev));
    stats.put("totalEvents", totalEvents);
    stats.put("eventsTrend", eventsTrend);
    stats.put("eventsChangePct", pct(eventsTrend, eventsPrev));
    stats.put("totalMembers", totalMembers);
    stats.put("membersTrend", membersTrend);
    stats.put("membersChangePct", pct(membersTrend, membersPrev));
    stats.put("totalDiscussions", totalDiscussions);
    stats.put("discussionsTrend", discussionsTrend);
    stats.put("discussionsChangePct", pct(discussionsTrend, discussionsPrev));
    data.put("stats", stats);

    // Platform overview: last 7 days + delta vs previous 7 days.
    long signupsLast7 = registrations.countByStatusAndRegisteredAtAfter(RegistrationStatus.REGISTERED, weekAgo);
    long signupsPrev7 =
        registrations.countByStatusAndRegisteredAtAfter(RegistrationStatus.REGISTERED, twoWeeksAgo) - signupsLast7;

    Map<String, Object> overview = new LinkedHashMap<>();
    overview.put("circlesCreatedLast7Days", Map.of("value", circlesTrend, "delta", circlesTrend - Math.max(0, circlesPrev)));
    overview.put("newMembersLast7Days", Map.of("value", membersTrend, "delta", membersTrend - Math.max(0, membersPrev)));
    overview.put("eventSignupsLast7Days", Map.of("value", signupsLast7, "delta", signupsLast7 - Math.max(0, signupsPrev7)));
    overview.put("activeDiscussionsLast7Days",
        Map.of("value", discussionsTrend, "delta", discussionsTrend - Math.max(0, discussionsPrev)));
    data.put("overview", overview);

    data.put("recentCircles", recentCircles());
    data.put("topCircles", topCircles(weekAgoOdt, weekAgo));
    data.put("upcomingEvents", upcomingEvents());
    data.put("circleDistribution", circleDistribution());
    data.put("recentActivity", recentActivity());
    data.put("growth", growth(range));
    data.put("users", userSummary());

    return data;
  }

  public Map<String, Object> analytics(String range) {
    OffsetDateTime nowOdt = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime weekAgoOdt = nowOdt.minusDays(7);
    Instant weekAgo = weekAgoOdt.toInstant();

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("growth", growth(range));
    data.put("overview", dashboard(range).get("overview"));
    data.put("circlePerformance", circlePerformance(weekAgoOdt, weekAgo));
    data.put("users", userSummary());
    return data;
  }

  private List<Map<String, Object>> recentCircles() {
    List<Circle> list =
        visibleCircles().stream()
            .sorted(Comparator.comparing(Circle::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(5)
            .toList();
    Map<UUID, String> organizerNames = organizerNameMap(list.stream().map(Circle::getOrganizerId).toList());
    Map<UUID, Role> organizerRoles = organizerRoleMap(list.stream().map(Circle::getOrganizerId).toList());
    List<Map<String, Object>> out = new ArrayList<>();
    for (Circle c : list) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", c.getId().toString());
      m.put("name", c.getName());
      m.put("description", c.getDescription());
      m.put("city", c.getCity());
      long approved =
          memberships.countByCircleIdAndStatusAndReaderIdNot(c.getId(), MembershipStatus.APPROVED, c.getOrganizerId());
      m.put("membersCount", c.getOrganizerId() != null ? approved + 1 : approved);
      m.put("organizerId", c.getOrganizerId() != null ? c.getOrganizerId().toString() : null);
      m.put("organizerName", organizerNames.getOrDefault(c.getOrganizerId(), "Unknown"));
      Role r = organizerRoles.get(c.getOrganizerId());
      m.put("organizerRole", r != null ? r.name() : "ORGANIZER");
      m.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
      m.put("status", c.getStatus() == CircleStatus.ACTIVE ? "Active" : "Inactive");
      m.put("coverImageUrl", c.getCoverImageUrl());
      out.add(m);
    }
    return out;
  }

  /**
   * Top circles by member count with per-circle discussions/events counts,
   * 7-day deltas and a composite 7-day activity score. Backs the dashboard
   * "Top Circles" table.
   */
  private List<Map<String, Object>> topCircles(OffsetDateTime weekAgoOdt, Instant weekAgoInstant) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Circle c : visibleCircles()) {
      long approved =
          memberships.countByCircleIdAndStatusAndReaderIdNot(c.getId(), MembershipStatus.APPROVED, c.getOrganizerId());
      long members = c.getOrganizerId() != null ? approved + 1 : approved;
      long discussionsCount = discussions.countByCircleId(c.getId());
      long eventsCount = events.findByCircleIdOrderByCreatedAtDesc(c.getId()).size();
      long membersDelta =
          memberships.countByCircleIdAndStatusAndDecidedAtAfter(c.getId(), MembershipStatus.APPROVED, weekAgoOdt);
      long discussionsDelta = discussions.countByCircleIdAndCreatedAtAfter(c.getId(), weekAgoInstant);
      long eventsDelta = events.countByCircleIdAndCreatedAtAfter(c.getId(), weekAgoInstant);
      long activity7d = membersDelta + discussionsDelta + eventsDelta;

      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", c.getId().toString());
      m.put("name", c.getName());
      m.put("category", c.getCategory() != null ? c.getCategory() : "General");
      m.put("city", c.getCity());
      m.put("membersCount", members);
      m.put("discussionsCount", discussionsCount);
      m.put("eventsCount", eventsCount);
      m.put("activity7d", activity7d);
      m.put("membersDelta", membersDelta);
      m.put("discussionsDelta", discussionsDelta);
      m.put("eventsDelta", eventsDelta);
      m.put("status", c.getStatus() == CircleStatus.ACTIVE ? "Active" : "Inactive");
      m.put("coverImageUrl", c.getCoverImageUrl());
      out.add(m);
    }
    out.sort((a, b) -> {
      int cmp = Long.compare((Long) b.get("membersCount"), (Long) a.get("membersCount"));
      return cmp != 0 ? cmp : Long.compare((Long) b.get("activity7d"), (Long) a.get("activity7d"));
    });
    return out.stream().limit(5).toList();
  }

  /**
   * Circle member distribution for the dashboard donut: per-circle member
   * counts plus their share of total members across all circles.
   */
  private Map<String, Object> circleDistribution() {
    List<Circle> all = visibleCircles();
    long total = 0;
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Circle c : all) {
      long approved =
          memberships.countByCircleIdAndStatusAndReaderIdNot(c.getId(), MembershipStatus.APPROVED, c.getOrganizerId());
      long members = c.getOrganizerId() != null ? approved + 1 : approved;
      total += members;
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", c.getId().toString());
      m.put("name", c.getName());
      m.put("membersCount", members);
      m.put("percent", 0.0);
      rows.add(m);
    }
    for (Map<String, Object> m : rows) {
      long members = (Long) m.get("membersCount");
      m.put("percent", total > 0 ? Math.round(members * 1000.0 / total) / 10.0 : 0.0);
    }
    rows.sort((a, b) -> Long.compare((Long) b.get("membersCount"), (Long) a.get("membersCount")));

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("total", total);
    out.put("items", rows);
    return out;
  }

  /**
   * Recent-activity feed synthesized from real tables (no dedicated audit log):
   * members joining, discussions created, events published, circles created/updated.
   */
  private List<Map<String, Object>> recentActivity() {
    List<Circle> visible = visibleCircles();
    Map<UUID, String> circleNames =
        visible.stream().collect(Collectors.toMap(Circle::getId, Circle::getName, (a, b) -> a));
    Set<UUID> visibleIds = circleNames.keySet();

    List<Membership> joined = memberships.findTop10ByStatusOrderByDecidedAtDesc(MembershipStatus.APPROVED);
    List<Discussion> recentDisc = discussions.findTop10ByOrderByCreatedAtDesc();

    Set<UUID> userIds = new HashSet<>();
    joined.forEach(m -> {
      if (m.getReaderId() != null) userIds.add(m.getReaderId());
    });
    recentDisc.forEach(d -> {
      if (d.getCreatedBy() != null) userIds.add(d.getCreatedBy());
    });
    Map<UUID, String> userNames =
        users.findAllById(userIds).stream()
            .collect(Collectors.toMap(u -> u.getId(), u -> u.getName() != null ? u.getName() : "Member", (a, b) -> a));

    List<Map<String, Object>> out = new ArrayList<>();

    for (Membership m : joined) {
      if (m.getDecidedAt() == null) continue;
      if (m.getCircleId() == null || !visibleIds.contains(m.getCircleId())) continue;
      String cn = circleNames.getOrDefault(m.getCircleId(), "a circle");
      out.add(activity("member_joined", "👤", "New member joined " + cn, cn,
          userNames.get(m.getReaderId()), m.getDecidedAt().toInstant()));
    }
    for (Discussion d : recentDisc) {
      if (d.getCreatedAt() == null) continue;
      if (d.getCircleId() == null || !visibleIds.contains(d.getCircleId())) continue;
      String cn = circleNames.getOrDefault(d.getCircleId(), "a circle");
      out.add(activity("discussion_created", "💬", "Book discussion created in " + cn, cn,
          userNames.get(d.getCreatedBy()), d.getCreatedAt()));
    }
    for (Event e : events.findTop10ByOrderByCreatedAtDesc()) {
      if (e.getCreatedAt() == null) continue;
      String cn = circleNames.getOrDefault(e.getCircleId(), "a circle");
      out.add(activity("event_published", "📅", "New event published: " + e.getTitle(), cn, null, e.getCreatedAt()));
    }
    for (Circle c : visible.stream()
        .sorted(Comparator.comparing(Circle::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
        .limit(5)
        .toList()) {
      if (c.getCreatedAt() == null) continue;
      out.add(activity("circle_created", "📚", "New circle created: " + c.getName(), c.getName(), null,
          c.getCreatedAt().toInstant()));
    }
    for (Circle c : visible.stream()
        .sorted(Comparator.comparing(Circle::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
        .limit(10)
        .toList()) {
      if (c.getUpdatedAt() == null || c.getCreatedAt() == null) continue;
      if (c.getUpdatedAt().isAfter(c.getCreatedAt().plusSeconds(60))) {
        out.add(activity("circle_updated", "✏️", "Circle updated: " + c.getName(), c.getName(), null,
            c.getUpdatedAt().toInstant()));
      }
    }

    out.sort(Comparator.comparing((Map<String, Object> m) -> (String) m.get("createdAt"),
        Comparator.nullsLast(Comparator.<String>reverseOrder())));
    return out.stream().limit(10).toList();
  }

  private Map<String, Object> activity(
      String type, String icon, String description, String circleName, String actorName, Instant at) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", type);
    m.put("icon", icon);
    m.put("description", description);
    m.put("circleName", circleName);
    m.put("actorName", actorName);
    m.put("createdAt", at != null ? at.toString() : null);
    return m;
  }

  private List<Map<String, Object>> upcomingEvents() {
    LocalDate today = LocalDate.now(java.time.ZoneId.systemDefault());
    java.time.LocalTime nowTime = java.time.LocalTime.now(java.time.ZoneId.systemDefault());
    List<Event> list = events.findUpcomingLandingEvents(today, nowTime, PageRequest.of(0, 6));
    Map<UUID, Circle> circleMap =
        circles.findAllById(list.stream().map(Event::getCircleId).collect(Collectors.toSet())).stream()
            .collect(Collectors.toMap(Circle::getId, c -> c, (a, b) -> a));
    DateTimeFormatter day = DateTimeFormatter.ofPattern("d");
    DateTimeFormatter mon = DateTimeFormatter.ofPattern("MMM");
    List<Map<String, Object>> out = new ArrayList<>();
    for (Event e : list) {
      Circle c = circleMap.get(e.getCircleId());
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", e.getId().toString());
      m.put("title", e.getTitle());
      m.put("readingTopic", e.getReadingTopic());
      m.put("circleId", e.getCircleId() != null ? e.getCircleId().toString() : null);
      m.put("circleName", c != null ? c.getName() : "Reading Circle");
      m.put("circleCoverImageUrl", c != null ? c.getCoverImageUrl() : null);
      m.put("eventDate", e.getEventDate() != null ? e.getEventDate().toString() : null);
      m.put("eventTime", e.getEventTime() != null ? e.getEventTime().toString() : null);
      String dateFormatted;
      if (e.getEventDate() != null) {
        dateFormatted =
            e.getEventDate().format(mon) + " " + e.getEventDate().format(day) + ", " + e.getEventDate().getYear()
                + (e.getEventTime() != null ? " · " + e.getEventTime().toString().substring(0, 5) : "");
      } else {
        dateFormatted = "Scheduled";
      }
      m.put("dateFormatted", dateFormatted);
      m.put("location", e.getLocation());
      m.put("venue", e.getVenue() != null ? e.getVenue() : "Online");
      m.put("mode", e.getMode());
      m.put("type", e.getType());
      m.put("capacity", e.getCapacity() != null ? e.getCapacity() : 0);
      m.put("registeredCount", registrations.countByEventIdAndStatus(e.getId(), RegistrationStatus.REGISTERED));
      m.put("coverImageUrl", e.getCoverImageUrl());
      m.put("status", e.getStatus() != null ? e.getStatus().name() : "PUBLISHED");
      out.add(m);
    }
    return out;
  }

  /** Real cumulative growth series bucketed by range: 30d -> 8 pts, 3m -> 12 pts, 1y -> 12 pts. */
  private Map<String, Object> growth(String range) {
    int days = switch (range == null ? "" : range.toLowerCase()) {
      case "3m", "3months", "90", "90d" -> 90;
      case "1y", "1year", "365", "12m" -> 365;
      default -> 30;
    };
    int points = days <= 30 ? 8 : 12;
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime start = now.minusDays(days);

    List<OffsetDateTime> userTimes =
        users.findAll().stream()
            .filter(u -> u.getCreatedAt() != null)
            .map(u -> u.getCreatedAt().isAfter(start.minusSeconds(1)) ? u.getCreatedAt() : null)
            .filter(t -> t != null)
            .sorted()
            .toList();
    List<Circle> visible = visibleCircles();
    List<OffsetDateTime> circleTimes =
        visible.stream()
            .filter(c -> c.getCreatedAt() != null)
            .map(c -> c.getCreatedAt().isAfter(start.minusSeconds(1)) ? c.getCreatedAt() : null)
            .filter(t -> t != null)
            .sorted()
            .toList();
    // Events carry Instant createdAt; normalise to OffsetDateTime UTC.
    List<OffsetDateTime> eventTimes =
        events.findAll().stream()
            .filter(e -> e.getCreatedAt() != null)
            .map(e -> OffsetDateTime.ofInstant(e.getCreatedAt(), ZoneOffset.UTC))
            .filter(t -> t.isAfter(start.minusSeconds(1)))
            .sorted()
            .toList();

    long baseMembers = Math.max(0, users.countByRoleIn(List.of(Role.READER, Role.ORGANIZER)) - userTimes.size());
    long baseCircles = Math.max(0, visible.size() - circleTimes.size());
    long baseEvents = Math.max(0, events.count() - eventTimes.size());

    DateTimeFormatter label = DateTimeFormatter.ofPattern("MMM d");
    List<String> labels = new ArrayList<>();
    List<Long> memberSeries = new ArrayList<>();
    List<Long> eventSeries = new ArrayList<>();
    List<Long> circleSeries = new ArrayList<>();

    for (int i = 0; i < points; i++) {
      OffsetDateTime bucketEnd =
          points == 1 ? now : start.plusDays((long) i * days / (points - 1));
      labels.add(bucketEnd.format(label));
      long m = baseMembers + userTimes.stream().filter(t -> !t.isAfter(bucketEnd)).count();
      long c = baseCircles + circleTimes.stream().filter(t -> !t.isAfter(bucketEnd)).count();
      long e = baseEvents + eventTimes.stream().filter(t -> !t.isAfter(bucketEnd)).count();
      memberSeries.add(m);
      circleSeries.add(c);
      eventSeries.add(e);
    }

    Map<String, Object> g = new LinkedHashMap<>();
    g.put("range", days <= 30 ? "30d" : days <= 90 ? "3m" : "1y");
    g.put("labels", labels);
    g.put("members", memberSeries);
    g.put("events", eventSeries);
    g.put("circles", circleSeries);
    return g;
  }

  private List<Map<String, Object>> circlePerformance(OffsetDateTime weekAgoOdt, Instant weekAgoInstant) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Circle c : visibleCircles()) {
      long approved =
          memberships.countByCircleIdAndStatusAndReaderIdNot(c.getId(), MembershipStatus.APPROVED, c.getOrganizerId());
      long members = c.getOrganizerId() != null ? approved + 1 : approved;
      long discussionsCount = discussions.countByCircleId(c.getId());
      long eventCount = events.findByCircleIdOrderByCreatedAtDesc(c.getId()).size();
      long membersDelta =
          memberships.countByCircleIdAndStatusAndDecidedAtAfter(c.getId(), MembershipStatus.APPROVED, weekAgoOdt);
      long discussionsDelta = discussions.countByCircleIdAndCreatedAtAfter(c.getId(), weekAgoInstant);
      long eventsDelta = events.countByCircleIdAndCreatedAtAfter(c.getId(), weekAgoInstant);
      long activity = membersDelta + discussionsDelta + eventsDelta;
      int activityRate = members > 0 ? (int) Math.min(100, Math.round((double) activity / members * 100)) : 0;
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", c.getId().toString());
      m.put("name", c.getName());
      m.put("category", c.getCategory() != null ? c.getCategory() : "General");
      m.put("city", c.getCity());
      m.put("membersCount", members);
      m.put("discussionsCount", discussionsCount);
      m.put("eventsCount", eventCount);
      m.put("activity7d", activity);
      m.put("activityRate", activityRate);
      m.put("status", c.getStatus() == CircleStatus.ACTIVE ? "Active" : "Inactive");
      out.add(m);
    }
    out.sort((a, b) -> Long.compare((Long) b.get("membersCount"), (Long) a.get("membersCount")));
    return out.stream().limit(10).toList();
  }

  private Map<String, Object> userSummary() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("totalAdmins", users.countByRole(Role.ADMIN));
    m.put("totalOrganizers", users.countByRole(Role.ORGANIZER));
    m.put("totalReaders", users.countByRole(Role.READER));
    m.put("activeMembers", users.countByActiveTrue());
    long pending = 0;
    try {
      pending = memberships.countByStatus(MembershipStatus.PENDING);
    } catch (Exception ignored) {
    }
    m.put("pendingRequests", pending);
    m.put("activeCircles", visibleCircles().stream().filter(Circle::isActive).count());
    return m;
  }

  private Map<UUID, String> organizerNameMap(List<UUID> ids) {
    return users.findAllById(ids.stream().filter(id -> id != null).toList()).stream()
        .collect(Collectors.toMap(
            u -> u.getId(), u -> u.getName() != null ? u.getName() : "Unknown", (a, b) -> a));
  }

  private Map<UUID, Role> organizerRoleMap(List<UUID> ids) {
    return users.findAllById(ids.stream().filter(id -> id != null).toList()).stream()
        .collect(Collectors.toMap(u -> u.getId(), u -> u.getRole(), (a, b) -> a));
  }

  /** Non-hidden circles only: hidden ones (Organisers Lounge) are chat groups, not circles. */
  private List<Circle> visibleCircles() {
    return circles.findAll().stream().filter(c -> !c.isHidden()).toList();
  }

  /** Percent change of current vs previous period; 100 when growing from zero. */
  private static int pct(long current, long previous) {
    if (previous <= 0) return current > 0 ? 100 : 0;
    return (int) Math.round((double) (current - previous) / previous * 100);
  }
}
