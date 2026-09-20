package com.readerscircle.events.dto;

import com.readerscircle.events.EventStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record UpdateEventRequest(
    String title,
    String description,
    String readingTopic,
    LocalDate eventDate,
    LocalTime eventTime,
    String venue,
    Integer capacity,
    Instant registrationDeadline,
    String coverImageUrl,
    EventStatus status
) {}
