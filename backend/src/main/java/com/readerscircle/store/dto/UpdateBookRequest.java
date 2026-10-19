package com.readerscircle.store.dto;

/** Edit Book modal payload — all fields optional, only non-null ones apply. */
public record UpdateBookRequest(
    String title,
    String author,
    String description,
    String coverImageUrl,
    Integer price,
    Integer stockQty,
    String status) {}
