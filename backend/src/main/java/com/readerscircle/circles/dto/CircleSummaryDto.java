package com.readerscircle.circles.dto;

import com.readerscircle.circles.CircleStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CircleSummaryDto(
    UUID id,
    String name,
    String city,
    String description,
    UUID organizerId,
    CircleStatus status,
    OffsetDateTime createdAt,
    long memberCount,
    String coverImageUrl,
    String category,
    List<String> tags,
    boolean hidden) {}
