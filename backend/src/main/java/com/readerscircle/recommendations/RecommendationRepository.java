package com.readerscircle.recommendations;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {
  List<Recommendation> findAllByOrderByCreatedAtDesc();

  List<Recommendation> findByRecommendedByOrderByCreatedAtDesc(UUID recommendedBy);
}
