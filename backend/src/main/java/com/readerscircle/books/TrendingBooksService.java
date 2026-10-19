package com.readerscircle.books;

import com.readerscircle.discussions.Discussion;
import com.readerscircle.discussions.DiscussionRepository;
import com.readerscircle.recommendations.Recommendation;
import com.readerscircle.recommendations.RecommendationRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trending + grouped books backing the reader right rail.
 * Groups by normalized (title + author) across discussions + recommendations,
 * so "12 discussions" and "Recommended by 24 readers" are server-computed.
 */
@Service
public class TrendingBooksService {

  private final DiscussionRepository discussions;
  private final RecommendationRepository recommendations;

  public TrendingBooksService(
      DiscussionRepository discussions, RecommendationRepository recommendations) {
    this.discussions = discussions;
    this.recommendations = recommendations;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> trending(int limit) {
    return grouped(limit, true);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> groupedByBook(int limit, String sort) {
    boolean byTrending = sort == null || sort.isBlank()
        || sort.toLowerCase().contains("trend")
        || sort.toLowerCase().contains("popular")
        || sort.toLowerCase().contains("discussion");
    return grouped(limit, byTrending);
  }

  private List<Map<String, Object>> grouped(int limit, boolean byTrending) {
    int lim = Math.max(1, Math.min(20, limit));
    Map<String, Agg> map = new LinkedHashMap<>();

    for (Discussion d : discussions.findAllByOrderByCreatedAtDesc()) {
      String key = key(d.getBookTitle(), d.getAuthor());
      Agg a = map.computeIfAbsent(key, k -> new Agg(d.getBookTitle(), d.getAuthor()));
      a.discussionCount++;
      a.views += d.getViewsCount();
      a.likes += d.getLikesCount();
      if (a.coverImageUrl == null && d.getCoverImageUrl() != null) {
        a.coverImageUrl = d.getCoverImageUrl();
      }
      if (a.genre == null && d.getGenre() != null) {
        a.genre = d.getGenre();
      }
    }

    for (Recommendation r : recommendations.findAllByOrderByCreatedAtDesc()) {
      String key = key(r.getBookTitle(), r.getAuthor());
      Agg a = map.computeIfAbsent(key, k -> new Agg(r.getBookTitle(), r.getAuthor()));
      a.recommendationCount++;
      a.upvotes += r.getUpvotesCount();
      a.recommenders.add(r.getRecommendedBy() == null ? null : r.getRecommendedBy().toString());
      if (a.coverImageUrl == null && r.getCoverImageUrl() != null) {
        a.coverImageUrl = r.getCoverImageUrl();
      }
      if (a.genre == null && r.getGenre() != null) {
        a.genre = r.getGenre();
      }
      if (a.author == null) {
        a.author = r.getAuthor();
      }
    }

    List<Agg> all = new ArrayList<>(map.values());
    if (byTrending) {
      all.sort(Comparator.comparingInt(Agg::score).reversed());
    } else {
      all.sort(Comparator.comparingInt((Agg a) -> a.recommenders.size()).reversed());
    }

    List<Map<String, Object>> out = new ArrayList<>();
    int rank = 1;
    for (Agg a : all.stream().limit(lim).toList()) {
      // drop null placeholder from recommenders set counting
      long distinctReaders = a.recommenders.stream().filter(id -> id != null).distinct().count();
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("rank", rank++);
      m.put("bookTitle", a.bookTitle);
      m.put("title", a.bookTitle);
      m.put("author", a.author);
      m.put("genre", a.genre);
      m.put("coverImageUrl", a.coverImageUrl);
      m.put("coverUrl", a.coverImageUrl);
      m.put("discussionCount", a.discussionCount);
      m.put("discussions", a.discussionCount);
      m.put("discussionsCount", a.discussionCount);
      m.put("recommendationCount", a.recommendationCount);
      m.put("distinctRecommenders", distinctReaders);
      m.put("recommendedByCount", distinctReaders);
      m.put("recommendedByReaders", distinctReaders);
      m.put("upvotesCount", a.upvotes);
      m.put("viewsCount", a.views);
      m.put("likesCount", a.likes + a.upvotes);
      m.put("score", a.score());
      out.add(m);
    }
    return out;
  }

  private static String key(String title, String author) {
    String t = title == null ? "" : title.trim().toLowerCase(Locale.ROOT);
    String a = author == null ? "" : author.trim().toLowerCase(Locale.ROOT);
    return t + "\u0000" + a;
  }

  private static final class Agg {
    String bookTitle;
    String author;
    String genre;
    String coverImageUrl;
    int discussionCount;
    int recommendationCount;
    int upvotes;
    int views;
    int likes;
    java.util.Set<String> recommenders = new java.util.HashSet<>();

    Agg(String bookTitle, String author) {
      this.bookTitle = bookTitle;
      this.author = author;
    }

    int score() {
      return discussionCount * 10 + recommendationCount * 5 + upvotes + views / 10;
    }
  }
}
