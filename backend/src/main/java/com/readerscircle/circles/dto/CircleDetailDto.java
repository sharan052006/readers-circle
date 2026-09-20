package com.readerscircle.circles.dto;

import com.readerscircle.circles.CircleStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CircleDetailDto(
    UUID id,
    String name,
    String city,
    String description,
    UUID organizerId,
    String organizerName,
    CircleStatus status,
    OffsetDateTime createdAt,
    long memberCount) {}
