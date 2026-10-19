package com.readerscircle.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Snapshot line of an order (Flyway V24 {@code store_order_items}).
 * Book data is snapshotted so later catalog edits never rewrite history.
 */
@Entity
@Table(name = "store_order_items")
public class StoreOrderItem {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "book_id")
  private UUID bookId;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(nullable = false, length = 255)
  private String author;

  @Column(name = "cover_image_url", columnDefinition = "TEXT")
  private String coverImageUrl;

  @Column(name = "price_rupees", nullable = false)
  private int priceRupees = 0;

  @Column(nullable = false)
  private int qty = 1;

  @Column(name = "line_total", nullable = false)
  private int lineTotal = 0;

  public StoreOrderItem() {}

  public UUID getId() {
    return id;
  }

  public UUID getOrderId() {
    return orderId;
  }

  public void setOrderId(UUID orderId) {
    this.orderId = orderId;
  }

  public UUID getBookId() {
    return bookId;
  }

  public void setBookId(UUID bookId) {
    this.bookId = bookId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  public String getCoverImageUrl() {
    return coverImageUrl;
  }

  public void setCoverImageUrl(String coverImageUrl) {
    this.coverImageUrl = coverImageUrl;
  }

  public int getPriceRupees() {
    return priceRupees;
  }

  public void setPriceRupees(int priceRupees) {
    this.priceRupees = priceRupees;
  }

  public int getQty() {
    return qty;
  }

  public void setQty(int qty) {
    this.qty = qty;
  }

  public int getLineTotal() {
    return lineTotal;
  }

  public void setLineTotal(int lineTotal) {
    this.lineTotal = lineTotal;
  }
}
