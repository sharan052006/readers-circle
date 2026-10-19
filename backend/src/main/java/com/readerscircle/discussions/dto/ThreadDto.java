package com.readerscircle.discussions.dto;

import java.util.List;

/** Thread card shape (contribution block with replies + likes). */
public record ThreadDto(
    String id,
    String title,
    String author,
    String authorAvatar,
    String timeAgo,
    boolean isSpoiler,
    String content,
    int repliesCount,
    int likesCount,
    boolean userLiked,
    List<CommentDto> comments) {}
