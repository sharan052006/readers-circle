package com.readerscircle.recommendations;

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
 * Comment under a recommendation (Flyway V14 {@code recommendation_comments}).
 */
@Entity
@Table(name = "recommendation_comments")
public class RecommendationComment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "recommendation_id", nullable = false)
  private UUID recommendationId;

  @Column(name = "author_id", nullable = false)
  private UUID authorId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected RecommendationComment() {}

  public RecommendationComment(UUID recommendationId, UUID authorId, String content) {
    this.recommendationId = recommendationId;
    this.authorId = authorId;
    this.content = content;
  }

  public UUID getId() {
    return id;
  }

  public UUID getRecommendationId() {
    return recommendationId;
  }

  public UUID getAuthorId() {
    return authorId;
  }

  public String getContent() {
    return content;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
