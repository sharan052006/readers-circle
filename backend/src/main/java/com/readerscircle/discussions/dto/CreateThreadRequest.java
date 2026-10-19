package com.readerscircle.discussions.dto;

import jakarta.validation.constraints.NotBlank;

/** Contribute box / new thread payload ({title, content}). */
public record CreateThreadRequest(@NotBlank String content, String title) {}
