package com.readerscircle.auth.dto;

import jakarta.validation.constraints.NotNull;
import com.readerscircle.auth.Role;

public record UpdateRoleRequest(@NotNull Role role) {}
