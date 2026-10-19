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
 * One like per user per discussion comment (Flyway V23 {@code discussion_comment_likes}).
 */
@Entity
@Table(
    name = "discussion_comment_likes",
    uniqueConstraints = @UniqueConstraint(columnNames = {"comment_id", "user_id"}))
public class DiscussionCommentLike {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "comment_id", nullable = false)
  private UUID commentId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected DiscussionCommentLike() {}

  public DiscussionCommentLike(UUID commentId, UUID userId) {
    this.commentId = commentId;
    this.userId = userId;
  }

  public UUID getCommentId() {
    return commentId;
  }

  public UUID getUserId() {
    return userId;
  }
}
