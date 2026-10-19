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
 * One like per user per thread (Flyway V13 {@code discussion_thread_likes}).
 */
@Entity
@Table(
    name = "discussion_thread_likes",
    uniqueConstraints = @UniqueConstraint(columnNames = {"thread_id", "user_id"}))
public class DiscussionThreadLike {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "thread_id", nullable = false)
  private UUID threadId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected DiscussionThreadLike() {}

  public DiscussionThreadLike(UUID threadId, UUID userId) {
    this.threadId = threadId;
    this.userId = userId;
  }

  public UUID getThreadId() {
    return threadId;
  }

  public UUID getUserId() {
    return userId;
  }
}
