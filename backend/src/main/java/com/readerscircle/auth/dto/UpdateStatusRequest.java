package com.readerscircle.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateStatusRequest(@NotNull Boolean active, @Size(max = 500) String reason) {}
