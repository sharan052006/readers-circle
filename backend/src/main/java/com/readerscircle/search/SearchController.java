package com.readerscircle.search;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Unified reader search: GET /api/search?q=... */
@RestController
@PreAuthorize("isAuthenticated()")
public class SearchController {

  private final SearchService service;

  public SearchController(SearchService service) {
    this.service = service;
  }

  @GetMapping("/api/search")
  public Map<String, Object> search(
      @RequestParam(name = "q", required = false, defaultValue = "") String q,
      @RequestParam(name = "query", required = false) String queryAlias,
      @RequestParam(name = "limit", required = false, defaultValue = "5") int limit) {
    String effective = (q != null && !q.isBlank()) ? q : (queryAlias == null ? "" : queryAlias);
    return service.search(effective, limit);
  }
}
