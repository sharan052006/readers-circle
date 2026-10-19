package com.readerscircle.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Sellable book for the Reader's Circle store (Flyway V24 {@code store_books}).
 * Backs admin Manage Books (CRUD + stock) and the reader Discover Books grid.
 */
@Entity
@Table(name = "store_books")
public class StoreBook {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(nullable = false, length = 255)
  private String author;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "cover_image_url", columnDefinition = "TEXT")
  private String coverImageUrl;

  @Column(name = "price_rupees", nullable = false)
  private int priceRupees = 0;

  @Column(name = "stock_qty", nullable = false)
  private int stockQty = 0;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private BookStatus status = BookStatus.AVAILABLE;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public StoreBook() {}

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
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

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
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

  public int getStockQty() {
    return stockQty;
  }

  public void setStockQty(int stockQty) {
    this.stockQty = stockQty;
  }

  public BookStatus getStatus() {
    return status;
  }

  public void setStatus(BookStatus status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
