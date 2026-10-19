package com.readerscircle.discussions.dto;

import java.util.List;

/**
 * Reply row shape with nested threading + likes for the discussion detail view.
 * The 5-arg constructor is kept for backward compatibility with existing callers/tests.
 */
public record CommentDto(
    String id,
    String author,
    String avatar,
    String content,
    String createdAt,
    String parentId,
    String replyToAuthor,
    int likesCount,
    boolean userLiked,
    List<CommentDto> replies) {

  public CommentDto(String id, String author, String avatar, String content, String createdAt) {
    this(id, author, avatar, content, createdAt, null, null, 0, false, List.of());
  }
}
