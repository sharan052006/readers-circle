package com.readerscircle.chat;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageLikeRepository extends JpaRepository<MessageLike, UUID> {

  long countByMessageId(UUID messageId);

  boolean existsByMessageIdAndUserId(UUID messageId, UUID userId);

  boolean existsByMessageIdAndUserIdAndEmoji(UUID messageId, UUID userId, String emoji);

  void deleteByMessageIdAndUserId(UUID messageId, UUID userId);

  void deleteByMessageIdAndUserIdAndEmoji(UUID messageId, UUID userId, String emoji);

  List<MessageLike> findByMessageIdIn(List<UUID> messageIds);
}
