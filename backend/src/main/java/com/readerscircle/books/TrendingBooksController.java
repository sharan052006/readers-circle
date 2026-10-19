package com.readerscircle.books;

import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reader right-rail endpoints:
 * - GET /api/books/trending -> ranked by discussion activity
 * - GET /api/recommendations/books -> grouped by book with distinct reader counts
 */
@RestController
@PreAuthorize("isAuthenticated()")
public class TrendingBooksController {

  private final TrendingBooksService service;

  public TrendingBooksController(TrendingBooksService service) {
    this.service = service;
  }

  @GetMapping("/api/books/trending")
  public List<Map<String, Object>> trending(
      @RequestParam(required = false, defaultValue = "5") int limit) {
    return service.trending(limit);
  }

  @GetMapping("/api/recommendations/books")
  public List<Map<String, Object>> grouped(
      @RequestParam(required = false, defaultValue = "10") int limit,
      @RequestParam(required = false, defaultValue = "trending") String sort) {
    return service.groupedByBook(limit, sort);
  }
}
