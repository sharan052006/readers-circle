package com.readerscircle.auth;

import com.readerscircle.auth.dto.UpdateRoleRequest;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

  @GetMapping
  public Page<UserDto> list(@PageableDefault(size = 20) Pageable pageable) {
    return service.list(pageable);
  }

  @GetMapping("/{id}")
  public UserDto get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PatchMapping("/{id}")
  public UserDto updateRole(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest req) {
    return service.updateRole(id, req);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivate(@PathVariable UUID id) {
    service.deactivate(id);
  }
}
