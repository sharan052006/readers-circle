package com.readerscircle.auth.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.readerscircle.auth.Role;

public record UserDto(
    UUID id,
    String name,
    String email,
    Role role,
    boolean active,
    OffsetDateTime createdAt,
    String bio,
    String profilePictureUrl,
    String blockedReason,
    OffsetDateTime blockedAt,
    boolean twoFactorEnabled,
    String city,
    java.util.List<String> interests) {

  public UserDto(UUID id, String name, String email, Role role, boolean active, OffsetDateTime createdAt) {
    this(id, name, email, role, active, createdAt, null, null, null, null, false, null, java.util.List.of());
  }
}
