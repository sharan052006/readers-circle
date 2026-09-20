package com.readerscircle.events.dto;

import java.time.Instant;
import java.util.UUID;

public record AttendeeDto(
    UUID readerId,
    String fullName,
    String email,
    Instant registeredAt
) {}
