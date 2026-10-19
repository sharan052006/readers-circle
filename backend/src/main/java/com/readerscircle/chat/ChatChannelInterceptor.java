package com.readerscircle.chat;

import com.readerscircle.auth.JwtService;
import com.readerscircle.auth.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class ChatChannelInterceptor implements ChannelInterceptor {

  private static final Pattern TOPIC_CIRCLE_PATTERN =
      Pattern.compile("^/topic/circles/([a-fA-F0-9\\-]+)(/.*)?$");
  private static final Pattern APP_CIRCLE_PATTERN =
      Pattern.compile("^/app/circles/([a-fA-F0-9\\-]+)/(send|typing)$");

  private final JwtService jwtService;
  private final ChatService chatService;
  private final com.readerscircle.auth.UserRepository userRepository;

  public ChatChannelInterceptor(
      JwtService jwtService,
      @Lazy ChatService chatService,
      com.readerscircle.auth.UserRepository userRepository) {
    this.jwtService = jwtService;
    this.chatService = chatService;
    this.userRepository = userRepository;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (accessor == null) {
      return message;
    }

    if (!accessor.isMutable()) {
      accessor = StompHeaderAccessor.wrap(message);
    }

    StompCommand command = accessor.getCommand();

    if (StompCommand.CONNECT.equals(command)) {
      handleConnect(accessor);
    } else if (StompCommand.SUBSCRIBE.equals(command)) {
      handleSubscribe(accessor);
    } else if (StompCommand.SEND.equals(command)) {
      handleSend(accessor);
    }

    return org.springframework.messaging.support.MessageBuilder.createMessage(
        message.getPayload(), accessor.getMessageHeaders());
  }

  private void handleConnect(StompHeaderAccessor accessor) {
    String authHeader = accessor.getFirstNativeHeader("Authorization");
    if (authHeader == null) {
      authHeader = accessor.getFirstNativeHeader("token");
    }

    if (authHeader != null) {
      String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
      try {
        Claims claims = jwtService.parse(token).getPayload();
        UUID userId = UUID.fromString(claims.getSubject());
        boolean active =
            userRepository.findById(userId).map(com.readerscircle.auth.User::isActive).orElse(false);
        if (!active) {
          throw new AccessDeniedException("Account has been blocked by the administrator");
        }
        String roleStr = claims.get("role", String.class);
        Role role = roleStr != null ? Role.valueOf(roleStr) : Role.READER;

        Authentication auth =
            new UsernamePasswordAuthenticationToken(
                userId.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        accessor.setUser(auth);
      } catch (JwtException | IllegalArgumentException e) {
        throw new AccessDeniedException("Invalid or expired JWT token for WebSocket connection", e);
      }
    }
  }

  private void handleSubscribe(StompHeaderAccessor accessor) {
    String destination = accessor.getDestination();
    if (destination == null) {
      return;
    }

    Matcher matcher = TOPIC_CIRCLE_PATTERN.matcher(destination);
    if (matcher.matches()) {
      UUID circleId;
      try {
        circleId = UUID.fromString(matcher.group(1));
      } catch (IllegalArgumentException e) {
        throw new AccessDeniedException("Invalid circle ID format in topic destination");
      }

      Authentication auth = (Authentication) accessor.getUser();
      if (auth == null || auth.getName() == null) {
        throw new AccessDeniedException("Unauthenticated STOMP subscription to " + destination);
      }

      UUID userId = UUID.fromString(auth.getName());
      Role role = extractRoleFromAuth(auth);

      try {
        chatService.assertCanAccess(circleId, userId, role);
      } catch (org.springframework.web.server.ResponseStatusException e) {
        throw new AccessDeniedException(e.getReason() != null ? e.getReason() : "Access denied to chat topic", e);
      }
    }
  }

  private void handleSend(StompHeaderAccessor accessor) {
    String destination = accessor.getDestination();
    if (destination == null) {
      return;
    }

    Matcher matcher = APP_CIRCLE_PATTERN.matcher(destination);
    if (matcher.matches()) {
      UUID circleId;
      try {
        circleId = UUID.fromString(matcher.group(1));
      } catch (IllegalArgumentException e) {
        throw new AccessDeniedException("Invalid circle ID format in send destination");
      }

      Authentication auth = (Authentication) accessor.getUser();
      if (auth == null || auth.getName() == null) {
        throw new AccessDeniedException("Unauthenticated STOMP send to " + destination);
      }

      UUID userId = UUID.fromString(auth.getName());
      Role role = extractRoleFromAuth(auth);

      try {
        if (destination.endsWith("/typing")) {
          chatService.assertCanAccess(circleId, userId, role);
        } else {
          chatService.assertCanSend(circleId, userId, role);
        }
      } catch (org.springframework.web.server.ResponseStatusException e) {
        throw new AccessDeniedException(e.getReason() != null ? e.getReason() : "Send denied to chat", e);
      }
    }
  }

  private Role extractRoleFromAuth(Authentication auth) {
    if (auth.getAuthorities() != null) {
      for (var authority : auth.getAuthorities()) {
        String authName = authority.getAuthority();
        if (authName.startsWith("ROLE_")) {
          try {
            return Role.valueOf(authName.substring(5));
          } catch (IllegalArgumentException ignored) {
          }
        }
      }
    }
    return Role.READER;
  }
}
