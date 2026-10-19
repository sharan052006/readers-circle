package com.readerscircle.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Per-year order number counter backing human IDs like {@code RC-2026-0012}
 * (Flyway V24 {@code store_order_counters}).
 */
@Entity
@Table(name = "store_order_counters")
public class StoreOrderCounter {

  @Id
  private Integer yr;

  @Column(name = "next_number", nullable = false)
  private int nextNumber = 1;

  public StoreOrderCounter() {}

  public StoreOrderCounter(Integer yr, int nextNumber) {
    this.yr = yr;
    this.nextNumber = nextNumber;
  }

  public Integer getYr() {
    return yr;
  }

  public int getNextNumber() {
    return nextNumber;
  }

  public void setNextNumber(int nextNumber) {
    this.nextNumber = nextNumber;
  }
}
