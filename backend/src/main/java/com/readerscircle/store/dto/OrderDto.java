package com.readerscircle.store.dto;

import java.util.List;

/** Full order shape for the Order Details modal + reader order views. */
public record OrderDto(
    String id,
    String orderNumber,
    String status,
    int subtotal,
    int shipping,
    int total,
    String paymentMethod,
    String paymentStatus,
    String transactionId,
    String paidAt,
    ReaderInfo reader,
    AddressDto address,
    List<OrderLineDto> items,
    List<TimelineEntry> timeline,
    String note,
    String trackingId,
    String orderDate,
    String createdAt) {

  public record ReaderInfo(String name, String email) {}

  public record OrderLineDto(
      String bookId,
      String title,
      String author,
      String coverImageUrl,
      int price,
      int qty,
      int lineTotal) {}

  public record TimelineEntry(String stage, String title, String detail, String at, boolean done) {}
}
