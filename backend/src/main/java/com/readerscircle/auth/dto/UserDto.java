package com.readerscircle.auth.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.readerscircle.auth.Role;

public record UserDto(
    UUID id, String name, String email, Role role, boolean active, OffsetDateTime createdAt) {}
