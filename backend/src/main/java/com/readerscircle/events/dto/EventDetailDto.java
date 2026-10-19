package com.readerscircle.events.dto;

import com.readerscircle.events.EventStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record EventDetailDto(
    UUID id,
    UUID circleId,
    String circleName,
    UUID organizerId,
    String title,
    String description,
    String readingTopic,
    LocalDate eventDate,
    LocalTime eventTime,
    String venue,
    Integer capacity,
    long registeredCount,
    Instant registrationDeadline,
    String coverImageUrl,
    EventStatus status,
    boolean isRegistered,
    boolean isOrganizerOrAdmin,
    Instant createdAt,
    String type,
    String mode,
    String guidelines,
    String location
) {}
