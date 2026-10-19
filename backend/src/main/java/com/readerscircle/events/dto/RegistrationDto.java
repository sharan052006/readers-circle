package com.readerscircle.events.dto;

import com.readerscircle.events.RegistrationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record RegistrationDto(
    UUID id,
    UUID eventId,
    String eventTitle,
    UUID circleId,
    UUID readerId,
    RegistrationStatus status,
    Instant registeredAt,
    Instant cancelledAt,
    LocalDate eventDate,
    LocalTime eventTime,
    String venue,
    String coverImageUrl,
    String circleName,
    Integer capacity,
    long registeredCount,
    boolean attended
) {
  public RegistrationDto(
      UUID id,
      UUID eventId,
      String eventTitle,
      UUID circleId,
      UUID readerId,
      RegistrationStatus status,
      Instant registeredAt,
      Instant cancelledAt) {
    this(id, eventId, eventTitle, circleId, readerId, status, registeredAt, cancelledAt, null, null, null, null, null, null, 0, false);
  }
}
