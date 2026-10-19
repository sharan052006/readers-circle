package com.readerscircle.store.dto;

import java.util.List;

/** Book card + row shape for Manage Books and Discover Books. */
public record BookDto(
    String id,
    String title,
    String author,
    String description,
    String coverImageUrl,
    int price,
    int stockQty,
    String status,
    boolean lowStock,
    String addedOn,
    String createdAt) {

  public static BookDto of(com.readerscircle.store.StoreBook b, int lowThreshold) {
    boolean low = b.getStockQty() > 0 && b.getStockQty() <= lowThreshold;
    return new BookDto(
        b.getId() == null ? null : b.getId().toString(),
        b.getTitle(),
        b.getAuthor(),
        b.getDescription(),
        b.getCoverImageUrl(),
        b.getPriceRupees(),
        b.getStockQty(),
        b.getStatus() == null ? "AVAILABLE" : b.getStatus().name(),
        low,
        b.getCreatedAt() == null ? null : b.getCreatedAt().toString(),
        b.getCreatedAt() == null ? null : b.getCreatedAt().toString());
  }

  /** Paged wrapper matching the frontend Page<T> contract. */
  public record Page<T>(List<T> content, int number, int size, long totalElements, int totalPages) {}
}
