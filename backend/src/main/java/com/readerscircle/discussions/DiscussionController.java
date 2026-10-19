package com.readerscircle.discussions;

import com.readerscircle.auth.Role;
import com.readerscircle.discussions.dto.CommentDto;
import com.readerscircle.discussions.dto.CreateDiscussionRequest;
import com.readerscircle.discussions.dto.CreateThreadRequest;
import com.readerscircle.discussions.dto.DiscussionDto;
import com.readerscircle.discussions.dto.ThreadDto;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
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
 * Book Discussions REST surface: list / detail / create / threads / comments / likes.
 * All routes require authentication (reader, organizer, admin).
 */
@RestController
@PreAuthorize("isAuthenticated()")
public class DiscussionController {

  private final DiscussionService service;

  public DiscussionController(DiscussionService service) {
    this.service = service;
  }

  @GetMapping("/api/discussions")
  public List<DiscussionDto> list(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) String genre,
      @RequestParam(required = false) String tab,
      Authentication authentication) {
    return service.list(query, genre, tab, getUserId(authentication));
  }

  @GetMapping("/api/discussions/{id}")
  public DiscussionDto getById(@PathVariable UUID id, Authentication authentication) {
    return service.getById(id, getUserId(authentication));
  }

  @PostMapping("/api/discussions")
  @ResponseStatus(HttpStatus.CREATED)
  public DiscussionDto create(
      @Valid @RequestBody CreateDiscussionRequest request, Authentication authentication) {
    return service.create(request, getUserId(authentication));
  }

  @PostMapping("/api/discussions/{id}/threads")
  @ResponseStatus(HttpStatus.CREATED)
  public ThreadDto addThread(
      @PathVariable UUID id,
      @RequestBody CreateThreadRequest request,
      Authentication authentication) {
    String content = request == null ? null : request.content();
    String title = request == null ? null : request.title();
    return service.addThread(id, title, content, getUserId(authentication));
  }

  /**
   * Accepts either a raw JSON string ("nice book") or an object
   * ({content|text|comment} + optional parentId|parentCommentId|replyTo).
   */
  @PostMapping("/api/discussions/threads/{threadId}/comments")
  @ResponseStatus(HttpStatus.CREATED)
  public CommentDto addComment(
      @PathVariable UUID threadId,
      @RequestBody JsonNode body,
      Authentication authentication) {
    String content = null;
    UUID parentId = null;
    if (body != null) {
      if (body.isTextual()) {
        content = body.asText();
      } else if (body.isObject()) {
        for (String key : new String[] {"content", "text", "comment", "message"}) {
          if (body.has(key) && body.get(key).isTextual()) {
            content = body.get(key).asText();
            break;
          }
        }
        for (String key : new String[] {"parentId", "parentCommentId", "replyTo", "replyToId"}) {
          if (body.has(key) && body.get(key).isTextual() && !body.get(key).asText().isBlank()) {
            try {
              parentId = UUID.fromString(body.get(key).asText().trim());
            } catch (IllegalArgumentException ignored) {}
            break;
          }
        }
      }
    }
    return service.addComment(threadId, content, getUserId(authentication), parentId);
  }

  @PostMapping("/api/discussions/threads/{threadId}/like")
  public Map<String, Object> toggleLike(
      @PathVariable UUID threadId, Authentication authentication) {
    return service.toggleLike(threadId, getUserId(authentication));
  }

  @PostMapping("/api/discussions/comments/{commentId}/like")
  public Map<String, Object> toggleCommentLike(
      @PathVariable UUID commentId, Authentication authentication) {
    return service.toggleCommentLike(commentId, getUserId(authentication));
  }

  @PostMapping("/api/discussions/{id}/like")
  public Map<String, Object> toggleDiscussionLike(
      @PathVariable UUID id, Authentication authentication) {
    return service.toggleDiscussionLike(id, getUserId(authentication));
  }

  @PostMapping("/api/discussions/{id}/comments")
  @ResponseStatus(HttpStatus.CREATED)
  public CommentDto addQuickComment(
      @PathVariable UUID id,
      @RequestBody JsonNode body,
      Authentication authentication) {
    String content = null;
    if (body != null) {
      if (body.isTextual()) {
        content = body.asText();
      } else if (body.isObject()) {
        for (String key : new String[] {"content", "text", "comment", "message"}) {
          if (body.has(key) && body.get(key).isTextual()) {
            content = body.get(key).asText();
            break;
          }
        }
      }
    }
    return service.addQuickComment(id, content, getUserId(authentication));
  }

  private UUID getUserId(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    return UUID.fromString(auth.getName());
  }

  @SuppressWarnings("unused")
  private Role getUserRole(Authentication auth) {
    if (auth == null) {
      return Role.READER;
    }
    if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
      return Role.ADMIN;
    }
    if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZER"))) {
      return Role.ORGANIZER;
    }
    return Role.READER;
  }
}
