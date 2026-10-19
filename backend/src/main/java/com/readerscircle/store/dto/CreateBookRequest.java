package com.readerscircle.store.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Add Book modal payload. Status defaults from stock when omitted. */
public record CreateBookRequest(
    @NotBlank String title,
    @NotBlank String author,
    String description,
    String coverImageUrl,
    @Min(0) Integer price,
    @Min(0) Integer stockQty,
    String status) {}
