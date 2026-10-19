package com.readerscircle.store.dto;

import java.util.List;

/** Cart line + cart summary for the reader cart page + topbar badge. */
public record CartItemDto(
    String id,
    String bookId,
    String title,
    String author,
    String coverImageUrl,
    int price,
    int stockQty,
    String status,
    int qty,
    int lineTotal) {

  public record CartDto(List<CartItemDto> items, int subtotal, int count) {}
}
