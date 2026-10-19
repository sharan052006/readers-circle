package com.readerscircle.circles;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Co-organizer roster with history (Flyway V19 {@code circle_organizers}).
 * One row per (circle, user); {@code removedAt == null} means current organizer.
 * The circle's primary {@code organizerId} is always mirrored here.
 */
@Entity
@Table(
    name = "circle_organizers",
    uniqueConstraints = @UniqueConstraint(name = "ux_circle_organizer", columnNames = {"circle_id", "user_id"}))
public class CircleOrganizer {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "circle_id", nullable = false)
  private UUID circleId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @CreationTimestamp
  @Column(name = "added_at", nullable = false)
  private OffsetDateTime addedAt;

  @Column(name = "removed_at")
  private OffsetDateTime removedAt;

  protected CircleOrganizer() {}

  public CircleOrganizer(UUID circleId, UUID userId) {
    this.circleId = circleId;
    this.userId = userId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getCircleId() {
    return circleId;
  }

  public UUID getUserId() {
    return userId;
  }

  public OffsetDateTime getAddedAt() {
    return addedAt;
  }

  public OffsetDateTime getRemovedAt() {
    return removedAt;
  }

  public boolean isCurrent() {
    return removedAt == null;
  }

  public void markRemoved() {
    this.removedAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
  }

  public void reinstate() {
    this.removedAt = null;
  }
}
