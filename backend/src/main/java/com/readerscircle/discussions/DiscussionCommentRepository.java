package com.readerscircle.discussions;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscussionCommentRepository extends JpaRepository<DiscussionComment, UUID> {
  List<DiscussionComment> findByThreadIdOrderByCreatedAtAsc(UUID threadId);

  long countByThreadId(UUID threadId);
}
