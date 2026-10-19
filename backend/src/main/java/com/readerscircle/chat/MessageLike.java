package com.readerscircle.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** Emoji reaction on a chat bubble (Flyway V12 heart, V21 multi-emoji). */
@Entity
@Table(
    name = "chat_message_likes",
    uniqueConstraints = @UniqueConstraint(name = "ux_chat_message_like_emoji", columnNames = {"message_id", "user_id", "emoji"}))
public class MessageLike {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "message_id", nullable = false)
  private UUID messageId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 16)
  private String emoji = "❤";

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected MessageLike() {}

  public MessageLike(UUID messageId, UUID userId) {
    this(messageId, userId, "❤");
  }

  public MessageLike(UUID messageId, UUID userId, String emoji) {
    this.messageId = messageId;
    this.userId = userId;
    this.emoji = emoji;
  }

  public UUID getId() {
    return id;
  }

  public UUID getMessageId() {
    return messageId;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getEmoji() {
    return emoji;
  }

  public void setEmoji(String emoji) {
    this.emoji = emoji;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
