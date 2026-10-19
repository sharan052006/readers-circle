package com.readerscircle.chat;

import com.readerscircle.auth.Role;
import com.readerscircle.chat.dto.SendMessageRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class ChatController {

  private final ChatService chatService;

  public ChatController(ChatService chatService) {
    this.chatService = chatService;
  }

  @MessageMapping("/circles/{circleId}/send")
  public void send(
      @DestinationVariable UUID circleId,
      @Valid SendMessageRequest request,
      Principal principal) {
    if (principal == null || principal.getName() == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated WebSocket message");
    }

    UUID senderId;
    try {
      senderId = UUID.fromString(principal.getName());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid principal identifier");
    }

    Role senderRole = Role.READER;
    if (principal instanceof Authentication auth && auth.getAuthorities() != null) {
      for (var authority : auth.getAuthorities()) {
        if (authority.getAuthority().startsWith("ROLE_")) {
          try {
            senderRole = Role.valueOf(authority.getAuthority().substring(5));
          } catch (IllegalArgumentException ignored) {
          }
        }
      }
    }

    chatService.sendMessage(circleId, senderId, senderRole, request.content(), request.attachmentUrl());
  }

  @MessageMapping("/circles/{circleId}/typing")
  public void typing(
      @DestinationVariable UUID circleId,
      Principal principal) {
    if (principal == null || principal.getName() == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated WebSocket message");
    }

    UUID senderId;
    try {
      senderId = UUID.fromString(principal.getName());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid principal identifier");
    }

    Role senderRole = Role.READER;
    if (principal instanceof Authentication auth && auth.getAuthorities() != null) {
      for (var authority : auth.getAuthorities()) {
        if (authority.getAuthority().startsWith("ROLE_")) {
          try {
            senderRole = Role.valueOf(authority.getAuthority().substring(5));
          } catch (IllegalArgumentException ignored) {
          }
        }
      }
    }

    chatService.broadcastTyping(circleId, senderId, senderRole);
  }
}
