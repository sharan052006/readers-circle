package com.readerscircle.auth;

import com.readerscircle.auth.dto.UpdateRoleRequest;
import com.readerscircle.auth.dto.UserDto;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin user management (SPEC-auth-identity.md). Deactivation is soft ({@code active=false});
 * history (memberships, messages) is kept — hard delete is out of scope.
 */
@Service
public class UserService {

  private final UserRepository users;
  private final PasswordEncoder encoder;

  public UserService(UserRepository users, PasswordEncoder encoder) {
    this.users = users;
    this.encoder = encoder;
  }

  @Transactional(readOnly = true)
  public Page<UserDto> list(@PageableDefault(size = 20) Pageable pageable) {
    return users.findAll(pageable).map(this::toDto);
  }

  @Transactional(readOnly = true)
  public UserDto get(UUID id) {
    return toDto(find(id));
  }

  @Transactional
  public UserDto updateRole(UUID id, UpdateRoleRequest req) {
    User user = find(id);
    user.setRole(req.role());
    return toDto(users.save(user));
  }

  @Transactional
  public void deactivate(UUID id) {
    User user = find(id);
    user.setActive(false);
    users.save(user);
  }

  /** Seed helper for tests/admin tooling (not exposed via REST). */
  @Transactional
  public User create(String name, String email, String password, Role role) {
    if (users.existsByEmailIgnoreCase(email.trim())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
    }
    try {
      // saveAndFlush: force the INSERT inside the try — see AuthService.register.
      return users.saveAndFlush(new User(name.trim(), email.trim(), encoder.encode(password), role));
    } catch (DataIntegrityViolationException race) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
    }
  }

  private User find(UUID id) {
    return users
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
  }

  private UserDto toDto(User u) {
    return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.isActive(), u.getCreatedAt());
  }
}
