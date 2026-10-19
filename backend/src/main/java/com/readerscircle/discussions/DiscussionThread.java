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
 * Contribution block inside a discussion (Flyway V13 {@code discussion_threads}).
 * Rendered as "Community Contribution" / "First Impressions" cards.
 */
@Entity
@Table(name = "discussion_threads")
public class DiscussionThread {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "discussion_id", nullable = false)
  private UUID discussionId;

  @Column(nullable = false, length = 255)
  private String title = "Community Contribution";

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "author_id", nullable = false)
  private UUID authorId;

  @Column(name = "likes_count", nullable = false)
  private int likesCount = 0;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected DiscussionThread() {}

  public DiscussionThread(UUID discussionId, String title, String content, UUID authorId) {
    this.discussionId = discussionId;
    this.title = title;
    this.content = content;
    this.authorId = authorId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getDiscussionId() {
    return discussionId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public UUID getAuthorId() {
    return authorId;
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
