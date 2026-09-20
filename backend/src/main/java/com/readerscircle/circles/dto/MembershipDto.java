package com.readerscircle.circles.dto;

import com.readerscircle.circles.MembershipStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MembershipDto(
    UUID id,
    UUID circleId,
    String circleName,
    String circleCity,
    UUID readerId,
    MembershipStatus status,
    OffsetDateTime requestedAt,
    OffsetDateTime decidedAt) {}
