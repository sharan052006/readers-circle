package com.readerscircle.recommendations;

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
 * One upvote per user per recommendation (Flyway V14 {@code recommendation_upvotes}).
 */
@Entity
@Table(
    name = "recommendation_upvotes",
    uniqueConstraints = @UniqueConstraint(columnNames = {"recommendation_id", "user_id"}))
public class RecommendationUpvote {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "recommendation_id", nullable = false)
  private UUID recommendationId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected RecommendationUpvote() {}

  public RecommendationUpvote(UUID recommendationId, UUID userId) {
    this.recommendationId = recommendationId;
    this.userId = userId;
  }

  public UUID getRecommendationId() {
    return recommendationId;
  }

  public UUID getUserId() {
    return userId;
  }
}
