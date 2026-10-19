package com.readerscircle.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageDto(
    UUID id,
    UUID circleId,
    UUID senderId,
    String senderName,
    String senderAvatarUrl,
    String content,
    Instant sentAt,
    Instant editedAt,
    boolean deleted,
    String attachmentUrl,
    long likeCount,
    boolean likedByMe,
    java.util.Map<String, Long> reactions,
    java.util.List<String> myReactions) {

  /** Backwards-compatible constructor (no social fields). */
  public ChatMessageDto(
      UUID id,
      UUID circleId,
      UUID senderId,
      String senderName,
      String content,
      Instant sentAt,
      Instant editedAt,
      boolean deleted) {
    this(id, circleId, senderId, senderName, null, content, sentAt, editedAt, deleted, null, 0, false,
        java.util.Map.of(), java.util.List.of());
  }

  /** Backwards-compatible constructor (heart-only social fields). */
  public ChatMessageDto(
      UUID id,
      UUID circleId,
      UUID senderId,
      String senderName,
      String content,
      Instant sentAt,
      Instant editedAt,
      boolean deleted,
      String attachmentUrl,
      long likeCount,
      boolean likedByMe) {
    this(id, circleId, senderId, senderName, null, content, sentAt, editedAt, deleted, attachmentUrl,
        likeCount, likedByMe, java.util.Map.of(), java.util.List.of());
  }
}
