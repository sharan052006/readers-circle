package com.readerscircle.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Maps to Flyway V5 {@code chat_messages} table (SPEC-circle-chat.md).
 * Represents a group chat message within a Circle.
 */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "circle_id", nullable = false)
  private UUID circleId;

  @Column(name = "sender_id", nullable = false)
  private UUID senderId;

  @Column(nullable = false, length = 2000)
  private String content;

  @CreationTimestamp
  @Column(name = "sent_at", nullable = false, updatable = false)
  private Instant sentAt;

  @Column(name = "edited_at")
  private Instant editedAt;

  @Column(nullable = false)
  private boolean deleted = false;

  @Column(name = "attachment_url", length = 1000)
  private String attachmentUrl;

  protected ChatMessage() {}

  public ChatMessage(UUID circleId, UUID senderId, String content) {
    this.circleId = circleId;
    this.senderId = senderId;
    this.content = content;
    this.sentAt = Instant.now();
    this.deleted = false;
  }

  public UUID getId() {
    return id;
  }

  public UUID getCircleId() {
    return circleId;
  }

  public UUID getSenderId() {
    return senderId;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public Instant getEditedAt() {
    return editedAt;
  }

  public void setEditedAt(Instant editedAt) {
    this.editedAt = editedAt;
  }

  public boolean isDeleted() {
    return deleted;
  }

  public void setDeleted(boolean deleted) {
    this.deleted = deleted;
  }

  public String getAttachmentUrl() {
    return attachmentUrl;
  }

  public void setAttachmentUrl(String attachmentUrl) {
    this.attachmentUrl = attachmentUrl;
  }
}
