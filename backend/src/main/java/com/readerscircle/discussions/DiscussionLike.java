package com.readerscircle.discussions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Direct like on a discussion root (Flyway V17 {@code discussion_likes}).
 */
@Entity
@Table(
    name = "discussion_likes",
    uniqueConstraints = @UniqueConstraint(columnNames = {"discussion_id", "user_id"}))
public class DiscussionLike {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "discussion_id", nullable = false)
  private UUID discussionId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected DiscussionLike() {}

  public DiscussionLike(UUID discussionId, UUID userId) {
    this.discussionId = discussionId;
    this.userId = userId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getDiscussionId() {
    return discussionId;
  }

  public UUID getUserId() {
    return userId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
