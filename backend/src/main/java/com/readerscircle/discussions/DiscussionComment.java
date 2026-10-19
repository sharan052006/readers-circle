package com.readerscircle.discussions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Reply under a thread (Flyway V13 {@code discussion_comments}).
 */
@Entity
@Table(name = "discussion_comments")
public class DiscussionComment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "thread_id", nullable = false)
  private UUID threadId;

  @Column(name = "author_id", nullable = false)
  private UUID authorId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "parent_comment_id")
  private UUID parentCommentId;

  @Column(name = "likes_count", nullable = false, columnDefinition = "integer default 0")
  private int likesCount = 0;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected DiscussionComment() {}

  public DiscussionComment(UUID threadId, UUID authorId, String content) {
    this(threadId, authorId, content, null);
  }

  public DiscussionComment(UUID threadId, UUID authorId, String content, UUID parentCommentId) {
    this.threadId = threadId;
    this.authorId = authorId;
    this.content = content;
    this.parentCommentId = parentCommentId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getThreadId() {
    return threadId;
  }

  public UUID getAuthorId() {
    return authorId;
  }

  public String getContent() {
    return content;
  }

  public UUID getParentCommentId() {
    return parentCommentId;
  }

  public void setParentCommentId(UUID parentCommentId) {
    this.parentCommentId = parentCommentId;
  }

  public int getLikesCount() {
    return likesCount;
  }

  public void setLikesCount(int likesCount) {
    this.likesCount = likesCount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
