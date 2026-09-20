package com.readerscircle.events.dto;

import com.readerscircle.events.RegistrationStatus;
import java.time.Instant;
import java.util.UUID;

public record RegistrationDto(
    UUID id,
    UUID eventId,
    String eventTitle,
    UUID circleId,
    UUID readerId,
    RegistrationStatus status,
    Instant registeredAt,
    Instant cancelledAt
) {}
