package com.readerscircle.discussions;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscussionRepository extends JpaRepository<Discussion, UUID> {
  List<Discussion> findAllByOrderByCreatedAtDesc();

  List<Discussion> findByCreatedByOrderByCreatedAtDesc(UUID createdBy);

  List<Discussion> findTop10ByOrderByCreatedAtDesc();

  long countByCreatedAtAfter(java.time.Instant after);

  long countByCircleId(UUID circleId);

  long countByCircleIdAndCreatedAtAfter(UUID circleId, java.time.Instant after);
}
