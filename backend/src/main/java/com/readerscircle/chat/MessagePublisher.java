package com.readerscircle.chat;

import com.readerscircle.chat.dto.ChatDeleteEventDto;
import com.readerscircle.chat.dto.ChatMessageDto;
import java.util.UUID;

/**
 * Publisher abstraction for broadcasting chat events.
 * Decouples controllers and domain logic from the underlying broker (in-memory STOMP in v1,
 * Redis Pub/Sub in Phase 3).
 */
public interface MessagePublisher {

  void publishMessage(UUID circleId, ChatMessageDto message);

  void publishDeletion(UUID circleId, ChatDeleteEventDto deletionEvent);

  /** Ephemeral typing indicator (not persisted). Default no-op for other publishers. */
  default void publishTyping(UUID circleId, UUID senderId, String senderName) {}
}
