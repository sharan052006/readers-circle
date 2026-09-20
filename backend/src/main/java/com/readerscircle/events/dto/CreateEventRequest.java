package com.readerscircle.events.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record CreateEventRequest(
    @NotBlank String title,
    String description,
    String readingTopic,
    LocalDate eventDate,
    LocalTime eventTime,
    String venue,
    Integer capacity,
    Instant registrationDeadline,
    String coverImageUrl,
    boolean publishNow
) {}
