package com.readerscircle.recommendations;

import com.fasterxml.jackson.databind.JsonNode;
import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Book Recommendations backend: list / create / upvote / comments / top-recommenders.
 * Responses are maps with alias keys (coverUrl/coverImageUrl, review/reviewText,
 * upvotesCount/upvoteCount/likesCount) so present and future clients both bind.
 */
@Service
public class RecommendationService {

  private final RecommendationRepository recommendations;
  private final RecommendationUpvoteRepository upvotes;
  private final RecommendationCommentRepository comments;
  private final UserRepository users;

  public RecommendationService(
      RecommendationRepository recommendations,
      RecommendationUpvoteRepository upvotes,
      RecommendationCommentRepository comments,
      UserRepository users) {
    this.recommendations = recommendations;
    this.upvotes = upvotes;
    this.comments = comments;
    this.users = users;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> list(String query, String genre, String sort, UUID callerId) {
    List<Recommendation> all = recommendations.findAllByOrderByCreatedAtDesc();
    String q = query == null ? "" : query.trim().toLowerCase();
    String g = genre == null ? "" : genre.trim().toLowerCase();
    List<Recommendation> filtered = new ArrayList<>();
    for (Recommendation r : all) {
      if (!g.isEmpty() && !"all".equals(g) && (r.getGenre() == null || !r.getGenre().toLowerCase().contains(g))) {
        continue;
      }
      if (!q.isEmpty()) {
        String hay =
            ((r.getBookTitle() == null ? "" : r.getBookTitle()) + " "
                    + (r.getAuthor() == null ? "" : r.getAuthor()) + " "
                    + (r.getReviewText() == null ? "" : r.getReviewText()) + " "
                    + (r.getGenre() == null ? "" : r.getGenre()))
                .toLowerCase();
        if (!hay.contains(q)) {
          continue;
        }
      }
      filtered.add(r);
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (Recommendation r : filtered) {
      out.add(toDto(r, callerId, false));
    }
    String s = sort == null ? "" : sort.trim().toLowerCase();
    if (s.contains("like") || s.contains("upvote") || s.contains("popular") || s.contains("top")) {
      out.sort(Comparator.comparingInt((Map<String, Object> m) -> (Integer) m.get("upvotesCount")).reversed());
    } else if (s.contains("rating") || s.contains("star")) {
      out.sort(Comparator.comparingInt((Map<String, Object> m) -> (Integer) m.get("rating")).reversed());
    }
    return out;
  }

  @Transactional
  public Map<String, Object> create(JsonNode body, UUID callerId) {
    String bookTitle = text(body, "bookTitle", "title", "book_title");
    String author = text(body, "author", "authors");
    if (bookTitle == null || bookTitle.isBlank() || author == null || author.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Book title and author are required");
    }
    Recommendation r = new Recommendation();
    r.setBookTitle(bookTitle.trim());
    r.setAuthor(author.trim());
    String genre = text(body, "genre", "genres", "category");
    r.setGenre(genre == null || genre.isBlank() ? "General" : genre.trim());
    int rating = 5;
    if (body != null && body.has("rating") && body.get("rating").canConvertToInt()) {
      rating = body.get("rating").asInt(5);
    }
    r.setRating(Math.max(1, Math.min(5, rating)));
    String review = text(body, "review", "reviewText", "review_text", "content", "description");
    r.setReviewText(review == null || review.isBlank() ? null : review.trim());
    boolean spoiler = bool(body, "spoiler", "containsSpoilers", "hasSpoilers", "spoilers");
    r.setSpoiler(spoiler);
    r.setTags(joinTags(body));
    String cover = text(body, "coverImageUrl", "coverUrl", "cover", "cover_image_url", "imageUrl");
    r.setCoverImageUrl(cover == null || cover.isBlank() ? null : cover.trim());
    r.setRecommendedBy(callerId);
    r.setUpvotesCount(0);
    recommendations.save(r);
    return toDto(r, callerId, true);
  }

  @Transactional
  public Map<String, Object> toggleUpvote(UUID id, UUID callerId) {
    Recommendation r =
        recommendations
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation not found"));
    boolean upvoted;
    if (upvotes.findByRecommendationIdAndUserId(id, callerId).isPresent()) {
      upvotes.deleteByRecommendationIdAndUserId(id, callerId);
      upvoted = false;
    } else {
      upvotes.save(new RecommendationUpvote(id, callerId));
      upvoted = true;
    }
    long count = upvotes.countByRecommendationId(id);
    r.setUpvotesCount((int) count);
    recommendations.save(r);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("upvotesCount", count);
    out.put("upvoteCount", count);
    out.put("likesCount", count);
    out.put("count", count);
    out.put("userUpvoted", upvoted);
    out.put("upvotedByMe", upvoted);
    return out;
  }

  @Transactional
  public Map<String, Object> addComment(UUID id, JsonNode body, UUID callerId) {
    Recommendation r =
        recommendations
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation not found"));
    String content = null;
    if (body != null) {
      if (body.isTextual()) {
        content = body.asText();
      } else if (body.isObject()) {
        content = text(body, "content", "text", "comment", "message", "review");
      }
    }
    if (content == null || content.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment text is required");
    }
    RecommendationComment c = new RecommendationComment(r.getId(), callerId, content.trim());
    comments.save(c);
    User author = users.findById(callerId).orElse(null);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", c.getId().toString());
    out.put("author", author == null ? "Member" : displayName(author));
    out.put("avatar", author == null ? null : author.getProfilePictureUrl());
    out.put("content", c.getContent());
    out.put("text", c.getContent());
    out.put("createdAt", c.getCreatedAt() == null ? null : c.getCreatedAt().toString());
    return out;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> topRecommenders() {
    Map<UUID, Long> counts = new LinkedHashMap<>();
    for (Recommendation r : recommendations.findAll()) {
      counts.put(r.getRecommendedBy(), counts.getOrDefault(r.getRecommendedBy(), 0L) + 1);
    }
    List<Map.Entry<UUID, Long>> sorted = new ArrayList<>(counts.entrySet());
    sorted.sort(Map.Entry.<UUID, Long>comparingByValue().reversed());
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map.Entry<UUID, Long> e : sorted.stream().limit(10).toList()) {
      User u = users.findById(e.getKey()).orElse(null);
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", e.getKey().toString());
      m.put("name", u == null ? "Member" : displayName(u));
      m.put("email", u == null ? null : u.getEmail());
      m.put("recommendationsCount", e.getValue());
      m.put("recs", e.getValue());
      m.put("count", e.getValue());
      out.add(m);
    }
    return out;
  }

  // ---- mapping ----

  private Map<String, Object> toDto(Recommendation r, UUID callerId, boolean includeComments) {
    User u = users.findById(r.getRecommendedBy()).orElse(null);
    String name = u == null ? "Member" : displayName(u);
    List<RecommendationComment> cc =
        comments.findByRecommendationIdOrderByCreatedAtAsc(r.getId());
    List<Map<String, Object>> commentDtos = new ArrayList<>();
    if (includeComments) {
      for (RecommendationComment c : cc) {
        User cu = users.findById(c.getAuthorId()).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId().toString());
        m.put("author", cu == null ? "Member" : displayName(cu));
        m.put("avatar", cu == null ? null : cu.getProfilePictureUrl());
        m.put("content", c.getContent());
        m.put("text", c.getContent());
        m.put("createdAt", c.getCreatedAt() == null ? null : c.getCreatedAt().toString());
        commentDtos.add(m);
      }
    }
    List<String> tags = splitTags(r.getTags());
    boolean upvoted = callerId != null && upvotes.findByRecommendationIdAndUserId(r.getId(), callerId).isPresent();
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", r.getId().toString());
    m.put("bookTitle", r.getBookTitle());
    m.put("title", r.getBookTitle());
    m.put("author", r.getAuthor());
    m.put("genre", r.getGenre());
    m.put("rating", r.getRating());
    m.put("review", r.getReviewText());
    m.put("reviewText", r.getReviewText());
    m.put("spoiler", r.isSpoiler());
    m.put("containsSpoilers", r.isSpoiler());
    m.put("tags", tags);
    m.put("coverImageUrl", r.getCoverImageUrl());
    m.put("coverUrl", r.getCoverImageUrl());
    m.put("recommenderName", name);
    m.put("recommendedBy", name);
    m.put("recommenderId", r.getRecommendedBy().toString());
    m.put("upvotesCount", r.getUpvotesCount());
    m.put("upvoteCount", r.getUpvotesCount());
    m.put("likesCount", r.getUpvotesCount());
    m.put("userUpvoted", upvoted);
    m.put("upvotedByMe", upvoted);
    m.put("commentsCount", cc.size());
    m.put("comments", commentDtos);
    m.put("createdAt", r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
    m.put("timeAgo", timeAgo(r.getCreatedAt()));
    return m;
  }

  private String displayName(User u) {
    if (u.getName() != null && !u.getName().isBlank()) {
      return u.getName();
    }
    return u.getEmail() == null ? "Member" : u.getEmail();
  }

  private static String text(JsonNode body, String... keys) {
    if (body == null || !body.isObject()) {
      return null;
    }
    for (String key : keys) {
      if (body.has(key) && body.get(key).isTextual()) {
        return body.get(key).asText();
      }
      if (body.has(key) && body.get(key).isNumber()) {
        return body.get(key).asText();
      }
    }
    return null;
  }

  private static boolean bool(JsonNode body, String... keys) {
    if (body == null || !body.isObject()) {
      return false;
    }
    for (String key : keys) {
      if (body.has(key)) {
        JsonNode n = body.get(key);
        if (n.isBoolean()) {
          return n.asBoolean(false);
        }
        if (n.isTextual()) {
          return "true".equalsIgnoreCase(n.asText().trim());
        }
      }
    }
    return false;
  }

  private static String joinTags(JsonNode body) {
    if (body == null || !body.isObject()) {
      return null;
    }
    if (body.has("tags")) {
      JsonNode n = body.get("tags");
      if (n.isArray()) {
        List<String> parts = new ArrayList<>();
        n.forEach(e -> {
          if (e.isTextual() && !e.asText().isBlank()) {
            parts.add(e.asText().trim());
          }
        });
        return parts.isEmpty() ? null : String.join(",", parts);
      }
      if (n.isTextual() && !n.asText().isBlank()) {
        return n.asText().trim();
      }
    }
    String single = text(body, "tag");
    return single == null || single.isBlank() ? null : single.trim();
  }

  private static List<String> splitTags(String tags) {
    if (tags == null || tags.isBlank()) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (String part : tags.split(",")) {
      if (!part.isBlank()) {
        out.add(part.trim());
      }
    }
    return out;
  }

  static String timeAgo(java.time.Instant at) {
    if (at == null) {
      return "Just now";
    }
    Duration d = Duration.between(at, Instant.now());
    long seconds = d.getSeconds();
    if (seconds < 60) {
      return "Just now";
    }
    long minutes = seconds / 60;
    if (minutes < 60) {
      return minutes + "m ago";
    }
    long hours = minutes / 60;
    if (hours < 24) {
      return hours + "h ago";
    }
    return (hours / 24) + "d ago";
  }
}
