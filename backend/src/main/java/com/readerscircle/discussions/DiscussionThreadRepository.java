package com.readerscircle.discussions;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscussionThreadRepository extends JpaRepository<DiscussionThread, UUID> {
  List<DiscussionThread> findByDiscussionIdOrderByCreatedAtAsc(UUID discussionId);

  long countByDiscussionId(UUID discussionId);
}
