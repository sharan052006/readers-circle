package com.readerscircle.circles.dto;

import com.readerscircle.circles.MembershipStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MemberDto(
    UUID membershipId,
    UUID readerId,
    String readerName,
    String readerEmail,
    MembershipStatus status,
    OffsetDateTime requestedAt,
    OffsetDateTime decidedAt) {}
