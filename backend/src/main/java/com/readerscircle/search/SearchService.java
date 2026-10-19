package com.readerscircle.search;

import com.readerscircle.circles.Circle;
import com.readerscircle.circles.CircleRepository;
import com.readerscircle.circles.CircleStatus;
import com.readerscircle.discussions.Discussion;
import com.readerscircle.discussions.DiscussionRepository;
import com.readerscircle.events.Event;
import com.readerscircle.events.EventRepository;
import com.readerscircle.recommendations.Recommendation;
import com.readerscircle.recommendations.RecommendationRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unified search backing the reader top bar
 * "Search books, discussions, circles...". Fans out across the four
 * per-domain stores and returns grouped slices (no new tables).
 */
@Service
public class SearchService {

  private final DiscussionRepository discussions;
  private final RecommendationRepository recommendations;
  private final CircleRepository circles;
  private final EventRepository events;

  public SearchService(
      DiscussionRepository discussions,
      RecommendationRepository recommendations,
      CircleRepository circles,
      EventRepository events) {
    this.discussions = discussions;
    this.recommendations = recommendations;
    this.circles = circles;
    this.events = events;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> search(String query, int limit) {
    String q = query == null ? "" : query.trim().toLowerCase();
    int lim = Math.max(1, Math.min(20, limit));
    if (q.isEmpty()) {
      Map<String, Object> empty = new LinkedHashMap<>();
      empty.put("query", "");
      empty.put("books", List.of());
      empty.put("discussions", List.of());
      empty.put("circles", List.of());
      empty.put("events", List.of());
      return empty;
    }

    List<Map<String, Object>> bookHits = new ArrayList<>();
    for (Recommendation r : recommendations.findAllByOrderByCreatedAtDesc()) {
      if (matches(q, r.getBookTitle(), r.getAuthor(), r.getGenre(), r.getReviewText())) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "book");
        m.put("bookTitle", r.getBookTitle());
        m.put("title", r.getBookTitle());
        m.put("author", r.getAuthor());
        m.put("coverImageUrl", r.getCoverImageUrl());
        m.put("recommendationId", r.getId().toString());
        bookHits.add(m);
        if (bookHits.size() >= lim) break;
      }
    }
    for (Discussion d : discussions.findAllByOrderByCreatedAtDesc()) {
      if (bookHits.size() >= lim) break;
      if (matches(q, d.getBookTitle(), d.getAuthor(), d.getGenre(), d.getDiscussionTitle())) {
        boolean already = bookHits.stream()
            .anyMatch(m -> equalsIgnoreCase((String) m.get("bookTitle"), d.getBookTitle()));
        if (!already) {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("type", "book");
          m.put("bookTitle", d.getBookTitle());
          m.put("title", d.getBookTitle());
          m.put("author", d.getAuthor());
          m.put("coverImageUrl", d.getCoverImageUrl());
          m.put("discussionId", d.getId().toString());
          bookHits.add(m);
        }
      }
    }

    List<Map<String, Object>> discussionHits = new ArrayList<>();
    for (Discussion d : discussions.findAllByOrderByCreatedAtDesc()) {
      if (matches(q, d.getBookTitle(), d.getAuthor(), d.getDiscussionTitle(),
          d.getDiscussionExcerpt(), d.getGenre(), d.getTag(), d.getTags())) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId().toString());
        m.put("bookTitle", d.getBookTitle());
        m.put("discussionTitle", d.getDiscussionTitle());
        m.put("author", d.getAuthor());
        m.put("coverImageUrl", d.getCoverImageUrl());
        discussionHits.add(m);
        if (discussionHits.size() >= lim) break;
      }
    }

    List<Map<String, Object>> circleHits = new ArrayList<>();
    for (Circle c : circles.findAllByOrderByCreatedAtDesc()) {
      if (c.isHidden() || c.getStatus() != CircleStatus.ACTIVE) continue;
      if (matches(q, c.getName(), c.getDescription(), c.getCity(), c.getCategory())) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId().toString());
        m.put("name", c.getName());
        m.put("city", c.getCity());
        m.put("category", c.getCategory());
        m.put("coverImageUrl", c.getCoverImageUrl());
        circleHits.add(m);
        if (circleHits.size() >= lim) break;
      }
    }

    List<Map<String, Object>> eventHits = new ArrayList<>();
    for (Event e : events.findAllByOrderByCreatedAtDesc()) {
      if (matches(q, e.getTitle(), e.getDescription(), e.getReadingTopic(), e.getVenue())) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId().toString());
        m.put("title", e.getTitle());
        m.put("venue", e.getVenue());
        m.put("eventDate", e.getEventDate() == null ? null : e.getEventDate().toString());
        m.put("coverImageUrl", e.getCoverImageUrl());
        eventHits.add(m);
        if (eventHits.size() >= lim) break;
      }
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("query", query.trim());
    out.put("books", bookHits);
    out.put("discussions", discussionHits);
    out.put("circles", circleHits);
    out.put("events", eventHits);
    return out;
  }

  private static boolean matches(String q, String... fields) {
    for (String f : fields) {
      if (f != null && f.toLowerCase().contains(q)) return true;
    }
    return false;
  }

  private static boolean equalsIgnoreCase(String a, String b) {
    if (a == null || b == null) return false;
    return a.trim().equalsIgnoreCase(b.trim());
  }
}
