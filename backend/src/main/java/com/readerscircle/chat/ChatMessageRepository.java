package com.readerscircle.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

  Page<ChatMessage> findByCircleIdAndDeletedFalseOrderBySentAtAsc(UUID circleId, Pageable pageable);

  List<ChatMessage> findByCircleIdAndDeletedFalseAndSentAtAfterOrderBySentAtAsc(UUID circleId, Instant since);

  List<ChatMessage> findByCircleIdAndDeletedFalseOrderBySentAtAsc(UUID circleId);

  java.util.Optional<ChatMessage> findFirstByCircleIdAndDeletedFalseOrderBySentAtDesc(UUID circleId);

  long countByDeletedFalse();

  long countByDeletedFalseAndSentAtAfter(java.time.Instant after);
}
