package com.readerscircle.discussions;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscussionCommentLikeRepository extends JpaRepository<DiscussionCommentLike, UUID> {
  Optional<DiscussionCommentLike> findByCommentIdAndUserId(UUID commentId, UUID userId);

  long countByCommentId(UUID commentId);
}
