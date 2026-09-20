package com.readerscircle.identity;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
public class UserAdminController {

  private final UserRepository users;

  public UserAdminController(UserRepository users) {
    this.users = users;
  }

  @GetMapping("/me")
  public UserResponse me(@AuthenticationPrincipal AuthUser principal) {
    User u =
        users
            .findById(principal.userId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    return UserResponse.from(u);
  }

  @GetMapping
  public List<UserResponse> list() {
    return users.findAll().stream().map(UserResponse::from).toList();
  }

  @GetMapping("/{id}")
  public UserResponse get(@PathVariable UUID id) {
    return users
        .findById(id)
        .map(UserResponse::from)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
  }

  @PatchMapping("/{id}")
  public UserResponse update(@PathVariable UUID id, @RequestBody UpdateUserRequest req) {
    User u =
        users
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
    if (req.role() != null) {
      try {
        u.setRole(Role.valueOf(req.role()));
      } catch (IllegalArgumentException e) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid role");
      }
    }
    if (req.deactivated() != null) {
      u.setDeactivated(req.deactivated());
    }
    if (req.name() != null && !req.name().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name update not supported in v1");
    }
    return UserResponse.from(users.save(u));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivate(@PathVariable UUID id) {
    User u =
        users
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
    u.setDeactivated(true);
    users.save(u);
  }
}
