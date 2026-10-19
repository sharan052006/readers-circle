package com.readerscircle.discussions;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscussionLikeRepository extends JpaRepository<DiscussionLike, UUID> {
  Optional<DiscussionLike> findByDiscussionIdAndUserId(UUID discussionId, UUID userId);
  long countByDiscussionId(UUID discussionId);
  void deleteByDiscussionIdAndUserId(UUID discussionId, UUID userId);
}
