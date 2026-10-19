package com.readerscircle.dashboard;

import com.readerscircle.auth.Role;
import com.readerscircle.books.TrendingBooksService;
import com.readerscircle.chat.ChatService;
import com.readerscircle.discussions.DiscussionService;
import com.readerscircle.events.RegistrationService;
import com.readerscircle.circles.MembershipService;
import com.readerscircle.notifications.NotificationService;
import com.readerscircle.recommendations.RecommendationService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reader home aggregate backing Dashboard / feed screen (any authenticated role). */
@Service
public class ReaderDashboardService {

  private final DiscussionService discussions;
  private final RecommendationService recommendations;
  private final TrendingBooksService trending;
  private final RegistrationService registrations;
  private final MembershipService memberships;
  private final NotificationService notifications;
  private final ChatService chat;

  public ReaderDashboardService(
      DiscussionService discussions,
      RecommendationService recommendations,
      TrendingBooksService trending,
      RegistrationService registrations,
      MembershipService memberships,
      NotificationService notifications,
      ChatService chat) {
    this.discussions = discussions;
    this.recommendations = recommendations;
    this.trending = trending;
    this.registrations = registrations;
    this.memberships = memberships;
    this.notifications = notifications;
    this.chat = chat;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> dashboard(UUID userId, Role role, int feedLimit) {
    int lim = Math.max(1, Math.min(20, feedLimit));

    List<?> feed = discussions.list(null, null, null, userId).stream().limit(lim).toList();
    List<Map<String, Object>> trendingBooks = trending.trending(3);
    List<Map<String, Object>> recommendedBooks = trending.groupedByBook(3, "recommended");
    // Fallback: if grouped is empty (no data), use raw recommendations list
    List<?> recommendedRaw = List.of();
    if (recommendedBooks.isEmpty()) {
      recommendedRaw = recommendations.list(null, null, "popular", userId).stream().limit(3).toList();
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("feed", feed);
    out.put("discussions", feed);
    out.put("trendingBooks", trendingBooks);
    out.put("recommendedBooks", recommendedBooks.isEmpty() ? recommendedRaw : recommendedBooks);
    out.put("registeredEvents", registrations.getMyRegistrations(userId));
    out.put("myMemberships", memberships.getMyMemberships(userId));
    out.put("unreadNotifications", notifications.unreadCount(userId).get("unreadCount"));
    try {
      out.put("chatUnread", chat.unreadCounts(userId, role));
    } catch (Exception ignored) {
      out.put("chatUnread", List.of());
    }
    return out;
  }
}
