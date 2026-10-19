package com.readerscircle.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One cart line per reader per book (Flyway V24 {@code store_cart_items}).
 */
@Entity
@Table(
    name = "store_cart_items",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "book_id"}))
public class StoreCartItem {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "book_id", nullable = false)
  private UUID bookId;

  @Column(nullable = false)
  private int qty = 1;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected StoreCartItem() {}

  public StoreCartItem(UUID userId, UUID bookId, int qty) {
    this.userId = userId;
    this.bookId = bookId;
    this.qty = qty;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getBookId() {
    return bookId;
  }

  public int getQty() {
    return qty;
  }

  public void setQty(int qty) {
    this.qty = qty;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
