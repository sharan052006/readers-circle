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

/**
 * Placed order with payment + shipping snapshots and per-stage timestamps
 * for the admin Order Timeline (Flyway V24 {@code store_orders}).
 */
@Entity
@Table(name = "store_orders")
public class StoreOrder {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "order_number", nullable = false, unique = true, length = 20)
  private String orderNumber;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OrderStatus status = OrderStatus.PENDING;

  @Column(name = "subtotal_rupees", nullable = false)
  private int subtotalRupees = 0;

  @Column(name = "shipping_rupees", nullable = false)
  private int shippingRupees = 0;

  @Column(name = "total_rupees", nullable = false)
  private int totalRupees = 0;

  @Column(name = "payment_method", nullable = false, length = 20)
  private String paymentMethod = "UPI";

  @Column(name = "payment_status", nullable = false, length = 20)
  private String paymentStatus = "PENDING";

  @Column(name = "transaction_id", length = 60)
  private String transactionId;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Column(name = "ship_name", nullable = false, length = 120)
  private String shipName;

  @Column(name = "ship_phone", nullable = false, length = 20)
  private String shipPhone;

  @Column(name = "ship_line1", nullable = false, length = 255)
  private String shipLine1;

  @Column(name = "ship_line2", length = 255)
  private String shipLine2;

  @Column(name = "ship_city", nullable = false, length = 120)
  private String shipCity;

  @Column(name = "ship_state", nullable = false, length = 120)
  private String shipState;

  @Column(name = "ship_pincode", nullable = false, length = 12)
  private String shipPincode;

  @Column(length = 500)
  private String note;

  @Column(name = "tracking_id", length = 80)
  private String trackingId;

  @Column(name = "pending_at", nullable = false)
  private Instant pendingAt;

  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  @Column(name = "shipped_at")
  private Instant shippedAt;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public StoreOrder() {}

  public UUID getId() {
    return id;
  }

  public String getOrderNumber() {
    return orderNumber;
  }

  public void setOrderNumber(String orderNumber) {
    this.orderNumber = orderNumber;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public void setStatus(OrderStatus status) {
    this.status = status;
  }

  public int getSubtotalRupees() {
    return subtotalRupees;
  }

  public void setSubtotalRupees(int subtotalRupees) {
    this.subtotalRupees = subtotalRupees;
  }

  public int getShippingRupees() {
    return shippingRupees;
  }

  public void setShippingRupees(int shippingRupees) {
    this.shippingRupees = shippingRupees;
  }

  public int getTotalRupees() {
    return totalRupees;
  }

  public void setTotalRupees(int totalRupees) {
    this.totalRupees = totalRupees;
  }

  public String getPaymentMethod() {
    return paymentMethod;
  }

  public void setPaymentMethod(String paymentMethod) {
    this.paymentMethod = paymentMethod;
  }

  public String getPaymentStatus() {
    return paymentStatus;
  }

  public void setPaymentStatus(String paymentStatus) {
    this.paymentStatus = paymentStatus;
  }

  public String getTransactionId() {
    return transactionId;
  }

  public void setTransactionId(String transactionId) {
    this.transactionId = transactionId;
  }

  public Instant getPaidAt() {
    return paidAt;
  }

  public void setPaidAt(Instant paidAt) {
    this.paidAt = paidAt;
  }

  public String getShipName() {
    return shipName;
  }

  public void setShipName(String shipName) {
    this.shipName = shipName;
  }

  public String getShipPhone() {
    return shipPhone;
  }

  public void setShipPhone(String shipPhone) {
    this.shipPhone = shipPhone;
  }

  public String getShipLine1() {
    return shipLine1;
  }

  public void setShipLine1(String shipLine1) {
    this.shipLine1 = shipLine1;
  }

  public String getShipLine2() {
    return shipLine2;
  }

  public void setShipLine2(String shipLine2) {
    this.shipLine2 = shipLine2;
  }

  public String getShipCity() {
    return shipCity;
  }

  public void setShipCity(String shipCity) {
    this.shipCity = shipCity;
  }

  public String getShipState() {
    return shipState;
  }

  public void setShipState(String shipState) {
    this.shipState = shipState;
  }

  public String getShipPincode() {
    return shipPincode;
  }

  public void setShipPincode(String shipPincode) {
    this.shipPincode = shipPincode;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public String getTrackingId() {
    return trackingId;
  }

  public void setTrackingId(String trackingId) {
    this.trackingId = trackingId;
  }

  public Instant getPendingAt() {
    return pendingAt;
  }

  public void setPendingAt(Instant pendingAt) {
    this.pendingAt = pendingAt;
  }

  public Instant getConfirmedAt() {
    return confirmedAt;
  }

  public void setConfirmedAt(Instant confirmedAt) {
    this.confirmedAt = confirmedAt;
  }

  public Instant getShippedAt() {
    return shippedAt;
  }

  public void setShippedAt(Instant shippedAt) {
    this.shippedAt = shippedAt;
  }

  public Instant getDeliveredAt() {
    return deliveredAt;
  }

  public void setDeliveredAt(Instant deliveredAt) {
    this.deliveredAt = deliveredAt;
  }

  public Instant getCancelledAt() {
    return cancelledAt;
  }

  public void setCancelledAt(Instant cancelledAt) {
    this.cancelledAt = cancelledAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
