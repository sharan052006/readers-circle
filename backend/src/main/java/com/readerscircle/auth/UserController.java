package com.readerscircle.auth;

import com.readerscircle.auth.dto.UpdateRoleRequest;
import com.readerscircle.auth.dto.UpdateStatusRequest;
import com.readerscircle.auth.dto.UserDto;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin-only user management. Belt-and-braces {@code @PreAuthorize} on top of the
 * {@code /api/users/** → ADMIN} rule in {@link SecurityConfig} — either layer denies alone.
 */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

  private final UserService service;

  public UserController(UserService service) {
    this.service = service;
  }

  /**
   * Paged user list with optional server-side filters for the User Management
   * screens: {@code ?query=} matches name/email, {@code ?role=ADMIN|ORGANIZER|READER},
   * {@code ?status=active|inactive} (inactive covers blocked/suspended).
   * Sorting stays on Spring's {@code ?sort=field,dir} (e.g. {@code sort=name,asc}).
   */
  @GetMapping
  public Page<UserDto> list(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) String role,
      @RequestParam(required = false) String status,
      @PageableDefault(size = 20) Pageable pageable) {
    return service.search(parseRole(role), parseActive(status), query, pageable);
  }

  @GetMapping("/organizers")
  public java.util.List<UserDto> listOrganizers() {
    return service.listEligibleOrganizers();
  }


  @GetMapping("/{id}")
  public UserDto get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PatchMapping("/{id}")
  public UserDto updateRole(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateRoleRequest req,
      org.springframework.security.core.Authentication authentication) {
    if (authentication != null
        && authentication.isAuthenticated()
        && id.toString().equals(authentication.getName())
        && req.role() != Role.ADMIN) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot demote yourself");
    }
    return service.updateRole(id, req);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivate(@PathVariable UUID id) {
    service.deactivate(id);
  }

  /** Block ({@code {"active": false}}) or unblock/reactivate ({@code {"active": true}}). */
  @PatchMapping("/{id}/status")
  public UserDto updateStatus(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateStatusRequest req,
      org.springframework.security.core.Authentication authentication) {
    if (!req.active()
        && authentication != null
        && authentication.isAuthenticated()
        && id.toString().equals(authentication.getName())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot block yourself");
    }
    return service.updateStatus(id, req.active(), req.reason());
  }

  private Role parseRole(String role) {
    if (role == null || role.isBlank() || "all".equalsIgnoreCase(role.trim())) {
      return null;
    }
    try {
      return Role.valueOf(role.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid role filter");
    }
  }

  private Boolean parseActive(String status) {
    if (status == null || status.isBlank() || "all".equalsIgnoreCase(status.trim())) {
      return null;
    }
    String s = status.trim().toLowerCase();
    if ("active".equals(s)) {
      return true;
    }
    if ("inactive".equals(s) || "blocked".equals(s) || "suspended".equals(s) || "banned".equals(s)) {
      return false;
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid status filter");
  }
}
