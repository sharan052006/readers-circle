package com.readerscircle.circles.dto;

import com.readerscircle.auth.Role;
import com.readerscircle.circles.MembershipStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MemberDto(
    UUID membershipId,
    UUID readerId,
    String readerName,
    String readerEmail,
    Role readerRole,
    MembershipStatus status,
    OffsetDateTime requestedAt,
    OffsetDateTime decidedAt,
    String bio,
    String avatarUrl,
    String city,
    List<String> interests) {

  /** Backwards-compatible constructor for existing callers (profile unknown). */
  public MemberDto(
      UUID membershipId,
      UUID readerId,
      String readerName,
      String readerEmail,
      MembershipStatus status,
      OffsetDateTime requestedAt,
      OffsetDateTime decidedAt) {
    this(membershipId, readerId, readerName, readerEmail, null, status, requestedAt, decidedAt,
        null, null, null, List.of());
  }

  /** Backwards-compatible constructor for existing callers (role known, profile unknown). */
  public MemberDto(
      UUID membershipId,
      UUID readerId,
      String readerName,
      String readerEmail,
      Role readerRole,
      MembershipStatus status,
      OffsetDateTime requestedAt,
      OffsetDateTime decidedAt) {
    this(membershipId, readerId, readerName, readerEmail, readerRole, status, requestedAt, decidedAt,
        null, null, null, List.of());
  }
}
