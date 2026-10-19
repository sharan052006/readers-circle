package com.readerscircle.discussions;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscussionThreadLikeRepository extends JpaRepository<DiscussionThreadLike, UUID> {
  Optional<DiscussionThreadLike> findByThreadIdAndUserId(UUID threadId, UUID userId);

  long countByThreadId(UUID threadId);

  void deleteByThreadIdAndUserId(UUID threadId, UUID userId);
}
