package com.readerscircle.chat;

import com.readerscircle.chat.dto.ChatDeleteEventDto;
import com.readerscircle.chat.dto.ChatMessageDto;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class SimpMessagePublisher implements MessagePublisher {

  private final SimpMessagingTemplate messagingTemplate;

  public SimpMessagePublisher(@Lazy SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  @Override
  public void publishMessage(UUID circleId, ChatMessageDto message) {
    messagingTemplate.convertAndSend("/topic/circles/" + circleId, message);
  }

  @Override
  public void publishDeletion(UUID circleId, ChatDeleteEventDto deletionEvent) {
    messagingTemplate.convertAndSend("/topic/circles/" + circleId + "/deletions", deletionEvent);
    // Also publish to main topic with deleted flag for clients subscribing to /topic/circles/{id} only
    ChatMessageDto tombstone =
        new ChatMessageDto(
            deletionEvent.messageId(),
            circleId,
            null,
            null,
            "",
            null,
            null,
            true);
    messagingTemplate.convertAndSend("/topic/circles/" + circleId, tombstone);
  }

  @Override
  public void publishTyping(UUID circleId, UUID senderId, String senderName) {
    Map<String, String> typing = new HashMap<>();
    typing.put("circleId", circleId.toString());
    typing.put("senderId", senderId != null ? senderId.toString() : null);
    typing.put("senderName", senderName != null ? senderName : "Member");
    messagingTemplate.convertAndSend("/topic/circles/" + circleId + "/typing", typing);
  }
}
