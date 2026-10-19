package com.readerscircle.chat.dto;

import jakarta.validation.constraints.Size;

public record SendMessageRequest(
    @Size(max = 2000, message = "Message content must be at most 2000 characters") String content,
    @Size(max = 1000, message = "Attachment URL must be at most 1000 characters") String attachmentUrl) {

  public SendMessageRequest(String content) {
    this(content, null);
  }
}
