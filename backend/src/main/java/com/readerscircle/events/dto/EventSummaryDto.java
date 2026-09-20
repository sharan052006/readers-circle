package com.readerscircle.events.dto;

import com.readerscircle.events.EventStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record EventSummaryDto(
    UUID id,
    UUID circleId,
    String title,
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
    Instant createdAt
) {}
