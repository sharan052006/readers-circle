package com.readerscircle.store;

/** Order lifecycle for admin Manage Orders + reader order views. */
public enum OrderStatus {
  PENDING,
  CONFIRMED,
  SHIPPED,
  DELIVERED,
  CANCELLED
}
