package com.readerscircle.events;

import com.readerscircle.auth.Role;
import com.readerscircle.events.dto.CreateEventRequest;
import com.readerscircle.events.dto.EventDetailDto;
import com.readerscircle.events.dto.EventSummaryDto;
import com.readerscircle.events.dto.UpdateEventRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class EventController {

  private final EventService eventService;

  public EventController(EventService eventService) {
    this.eventService = eventService;
  }

  @GetMapping("/api/circles/{id}/events")
  public List<EventSummaryDto> listEvents(
      @PathVariable UUID id,
      @RequestParam(required = false, defaultValue = "upcoming") String scope,
      Authentication authentication) {
    UUID actorId = getUserIdOrNull(authentication);
    return eventService.listEventsForCircle(id, scope, actorId);
  }

  @GetMapping("/api/events/{id}")
  public EventDetailDto getEvent(
      @PathVariable UUID id,
      Authentication authentication) {
    UUID actorId = getUserIdOrNull(authentication);
    Role actorRole = getUserRole(authentication);
    return eventService.getEvent(id, actorId, actorRole);
  }

  @PostMapping("/api/circles/{id}/events")
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.CREATED)
  public EventDetailDto createEvent(
      @PathVariable UUID id,
      @Valid @RequestBody CreateEventRequest request,
      Authentication authentication) {
    UUID actorId = getUserId(authentication);
    Role actorRole = getUserRole(authentication);
    return eventService.createEvent(id, request, actorId, actorRole);
  }

  @PatchMapping("/api/events/{id}")
  @PreAuthorize("isAuthenticated()")
  public EventDetailDto updateEvent(
      @PathVariable UUID id,
      @RequestBody UpdateEventRequest request,
      Authentication authentication) {
    UUID actorId = getUserId(authentication);
    Role actorRole = getUserRole(authentication);
    return eventService.updateEvent(id, request, actorId, actorRole);
  }

  @DeleteMapping("/api/events/{id}")
  @PreAuthorize("isAuthenticated()")
  public EventDetailDto cancelEvent(
      @PathVariable UUID id,
      Authentication authentication) {
    UUID actorId = getUserId(authentication);
    Role actorRole = getUserRole(authentication);
    return eventService.cancelEvent(id, actorId, actorRole);
  }

  private UUID getUserId(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    return UUID.fromString(auth.getName());
  }

  private UUID getUserIdOrNull(Authentication auth) {
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
      return null;
    }
    try {
      return UUID.fromString(auth.getName());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private Role getUserRole(Authentication auth) {
    if (auth == null) return Role.READER;
    if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
      return Role.ADMIN;
    }
    if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZER"))) {
      return Role.ORGANIZER;
    }
    return Role.READER;
  }
}
