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
    OffsetDateTime decidedAt,
    String circleCoverImageUrl,
    long circleMemberCount) {

  /** Backwards-compatible constructor for existing callers (card fields unknown). */
  public MembershipDto(
      UUID id,
      UUID circleId,
      String circleName,
      String circleCity,
      UUID readerId,
      MembershipStatus status,
      OffsetDateTime requestedAt,
      OffsetDateTime decidedAt) {
    this(id, circleId, circleName, circleCity, readerId, status, requestedAt, decidedAt, null, 0);
  }
}
