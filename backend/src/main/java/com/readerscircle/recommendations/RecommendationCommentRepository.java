package com.readerscircle.recommendations;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationCommentRepository extends JpaRepository<RecommendationComment, UUID> {
  List<RecommendationComment> findByRecommendationIdOrderByCreatedAtAsc(UUID recommendationId);

  long countByRecommendationId(UUID recommendationId);
}
