package com.readerscircle.identity;

import java.util.UUID;

public record UserResponse(UUID id, String name, String email, String role, boolean deactivated) {
  public static UserResponse from(User u) {
    return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.isDeactivated());
  }
}
