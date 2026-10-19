package com.readerscircle.events;

import com.readerscircle.auth.Role;
import com.readerscircle.events.dto.AttendeeDto;
import com.readerscircle.events.dto.RegistrationDto;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class RegistrationController {

  private final RegistrationService registrationService;

  public RegistrationController(RegistrationService registrationService) {
    this.registrationService = registrationService;
  }

  @PostMapping({"/api/events/{id}/register", "/api/events/{id}/registrations"})
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.CREATED)
  public RegistrationDto register(
      @PathVariable UUID id,
      Authentication authentication) {
    UUID readerId = getUserId(authentication);
    Role readerRole = getUserRole(authentication);
    return registrationService.register(id, readerId, readerRole);
  }

  @DeleteMapping({"/api/events/{id}/register", "/api/events/{id}/registrations"})
  @PreAuthorize("isAuthenticated()")
  public RegistrationDto cancelRegistration(
      @PathVariable UUID id,
      Authentication authentication) {
    UUID readerId = getUserId(authentication);
    return registrationService.cancelRegistration(id, readerId);
  }

  @GetMapping("/api/events/{id}/registrations")
  @PreAuthorize("isAuthenticated()")
  public List<AttendeeDto> getAttendees(
      @PathVariable UUID id,
      Authentication authentication) {
    UUID actorId = getUserId(authentication);
    Role actorRole = getUserRole(authentication);
    return registrationService.getAttendees(id, actorId, actorRole);
  }

  @GetMapping("/api/users/me/registrations")
  @PreAuthorize("isAuthenticated()")
  public List<RegistrationDto> getMyRegistrations(Authentication authentication) {
    UUID readerId = getUserId(authentication);
    return registrationService.getMyRegistrations(readerId);
  }

  /** Event Stats rails (Registered tab + Past tab). */
  @GetMapping("/api/users/me/event-stats")
  @PreAuthorize("isAuthenticated()")
  public Map<String, Long> getMyEventStats(Authentication authentication) {
    return registrationService.getMyEventStats(getUserId(authentication));
  }

  /**
   * Member-visible attendee faces for event cards/detail (names + avatars,
   * no emails — the full email roster stays organizer/admin-only).
   */
  @GetMapping("/api/events/{id}/attendee-preview")
  @PreAuthorize("isAuthenticated()")
  public List<Map<String, Object>> getAttendeePreview(
      @PathVariable UUID id, Authentication authentication) {
    return registrationService.getAttendeePreview(
        id, getUserId(authentication), getUserRole(authentication));
  }

  public record AttendanceRequest(Boolean attended) {}

  /** Organizer/admin marks a registered reader attended or not. */
  @PatchMapping("/api/events/{id}/attendees/{readerId}")
  @PreAuthorize("isAuthenticated()")
  public AttendeeDto markAttendance(
      @PathVariable UUID id,
      @PathVariable UUID readerId,
      @RequestBody(required = false) AttendanceRequest req,
      Authentication authentication) {
    boolean attended = req == null || req.attended() == null || req.attended();
    return registrationService.markAttendance(
        id, readerId, attended, getUserId(authentication), getUserRole(authentication));
  }

  private UUID getUserId(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    return UUID.fromString(auth.getName());
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
