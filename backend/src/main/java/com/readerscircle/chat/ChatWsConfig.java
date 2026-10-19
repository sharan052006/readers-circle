package com.readerscircle.chat;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class ChatWsConfig implements WebSocketMessageBrokerConfigurer {

  private static final List<String> DEFAULT_ORIGINS =
      List.of(
          "http://localhost:5173",
          "http://localhost:5174",
          "http://127.0.0.1:5173",
          "http://127.0.0.1:5174");

  private final ChatChannelInterceptor chatChannelInterceptor;
  private final List<String> allowedOrigins;

  public ChatWsConfig(
      ChatChannelInterceptor chatChannelInterceptor,
      @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:5174,http://127.0.0.1:5173,http://127.0.0.1:5174}")
          List<String> allowedOrigins) {
    this.chatChannelInterceptor = chatChannelInterceptor;
    this.allowedOrigins = allowedOrigins;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    // /topic for subscription broadcasts
    config.enableSimpleBroker("/topic");
    // /app for application message handling
    config.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    String[] origins = resolveOrigins().toArray(new String[0]);

    // General /ws endpoint
    registry.addEndpoint("/ws")
        .setAllowedOrigins(origins);
    registry.addEndpoint("/ws")
        .setAllowedOrigins(origins)
        .withSockJS();

    // Circle-specific /ws/circles/{circleId} endpoint per spec
    registry.addEndpoint("/ws/circles/{circleId}")
        .setAllowedOrigins(origins);
    registry.addEndpoint("/ws/circles/{circleId}")
        .setAllowedOrigins(origins)
        .withSockJS();
  }

  private List<String> resolveOrigins() {
    List<String> raw = this.allowedOrigins == null ? List.of() : this.allowedOrigins;
    List<String> parsed =
        raw.stream()
            .flatMap(s -> Arrays.stream(s.split(",")))
            .map(String::trim)
            .map(s -> s.replaceAll("/+$", ""))
            .filter(s -> !s.isEmpty() && !s.equals("*"))
            .toList();
    return parsed.isEmpty() ? DEFAULT_ORIGINS : parsed;
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(chatChannelInterceptor);
  }
}
