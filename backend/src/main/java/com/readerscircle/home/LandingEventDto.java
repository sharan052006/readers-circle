package com.readerscircle.home;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record LandingEventDto(
    UUID id,
    UUID circleId,
    String circleName,
    String title,
    String readingTopic,
    LocalDate eventDate,
    LocalTime eventTime,
    String venue,
    String coverImageUrl,
    long registeredCount,
    Integer capacity
) {}
