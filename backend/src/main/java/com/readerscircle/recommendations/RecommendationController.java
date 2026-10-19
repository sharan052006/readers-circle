package com.readerscircle.recommendations;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Book Recommendations REST surface: list / create / upvote / comments / top-recommenders.
 * All routes require authentication (reader, organizer, admin).
 */
@RestController
@PreAuthorize("isAuthenticated()")
public class RecommendationController {

  private final RecommendationService service;

  public RecommendationController(RecommendationService service) {
    this.service = service;
  }

  @GetMapping("/api/recommendations")
  public List<Map<String, Object>> list(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) String genre,
      @RequestParam(required = false) String sort,
      Authentication authentication) {
    return service.list(query, genre, sort, getUserId(authentication));
  }

  @GetMapping("/api/recommendations/top-recommenders")
  public List<Map<String, Object>> topRecommenders() {
    return service.topRecommenders();
  }

  @PostMapping("/api/recommendations")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(
      @RequestBody JsonNode body, Authentication authentication) {
    return service.create(body, getUserId(authentication));
  }

  @PostMapping("/api/recommendations/{id}/upvote")
  public Map<String, Object> toggleUpvote(
      @PathVariable UUID id, Authentication authentication) {
    return service.toggleUpvote(id, getUserId(authentication));
  }

  /**
   * Accepts either a raw JSON string ("great pick") or an object
   * ({content|text|comment|message}).
   */
  @PostMapping("/api/recommendations/{id}/comments")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> addComment(
      @PathVariable UUID id,
      @RequestBody JsonNode body,
      Authentication authentication) {
    return service.addComment(id, body, getUserId(authentication));
  }

  private UUID getUserId(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    return UUID.fromString(auth.getName());
  }
}
