package com.readerscircle.recommendations;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationUpvoteRepository extends JpaRepository<RecommendationUpvote, UUID> {
  Optional<RecommendationUpvote> findByRecommendationIdAndUserId(UUID recommendationId, UUID userId);

  long countByRecommendationId(UUID recommendationId);

  void deleteByRecommendationIdAndUserId(UUID recommendationId, UUID userId);
}
