package com.readerscircle.circles;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Maps 1:1 to Flyway V2 {@code memberships} table.
 * Enforces explicit state machine transitions per SPEC-circles-membership.md.
 */
@Entity
@Table(
    name = "memberships",
    uniqueConstraints = @UniqueConstraint(name = "ux_memberships_circle_reader", columnNames = {"circle_id", "reader_id"}))
public class Membership {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "circle_id", nullable = false)
  private UUID circleId;

  @Column(name = "reader_id", nullable = false)
  private UUID readerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MembershipStatus status = MembershipStatus.PENDING;

  @CreationTimestamp
  @Column(name = "requested_at", nullable = false)
  private OffsetDateTime requestedAt;

  @Column(name = "decided_at")
  private OffsetDateTime decidedAt;

  protected Membership() {}

  public Membership(UUID circleId, UUID readerId) {
    this.circleId = circleId;
    this.readerId = readerId;
    this.status = MembershipStatus.PENDING;
    this.requestedAt = OffsetDateTime.now();
  }

  public static Membership pending(UUID circleId, UUID readerId) {
    return new Membership(circleId, readerId);
  }

  /**
   * Re-submits a previously REJECTED application.
   * If already PENDING or APPROVED, throws 409 Conflict per spec.
   */
  public Membership resubmit() {
    if (this.status == MembershipStatus.PENDING) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Join request already pending");
    }
    if (this.status == MembershipStatus.APPROVED) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Already an approved member of this circle");
    }
    this.status = MembershipStatus.PENDING;
    this.requestedAt = OffsetDateTime.now();
    this.decidedAt = null;
    return this;
  }

  public void approve() {
    this.status = MembershipStatus.APPROVED;
    this.decidedAt = OffsetDateTime.now();
  }

  public void reject() {
    this.status = MembershipStatus.REJECTED;
    this.decidedAt = OffsetDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getCircleId() {
    return circleId;
  }

  public UUID getReaderId() {
    return readerId;
  }

  public MembershipStatus getStatus() {
    return status;
  }

  public OffsetDateTime getRequestedAt() {
    return requestedAt;
  }

  public OffsetDateTime getDecidedAt() {
    return decidedAt;
  }
}
