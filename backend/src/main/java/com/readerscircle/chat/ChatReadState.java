package com.readerscircle.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/** Per-user per-circle read watermark for unread badges. Maps to V12 {@code chat_read_state}. */
@Entity
@Table(
    name = "chat_read_state",
    uniqueConstraints = @UniqueConstraint(name = "ux_chat_read_state", columnNames = {"circle_id", "reader_id"}))
public class ChatReadState {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "circle_id", nullable = false)
  private UUID circleId;

  @Column(name = "reader_id", nullable = false)
  private UUID readerId;

  @Column(name = "last_read_at", nullable = false)
  private Instant lastReadAt = Instant.now();

  protected ChatReadState() {}

  public ChatReadState(UUID circleId, UUID readerId, Instant lastReadAt) {
    this.circleId = circleId;
    this.readerId = readerId;
    this.lastReadAt = lastReadAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getCircleId() {
    return circleId;
  }

  public UUID getReaderId() {
    return readerId;
  }

  public Instant getLastReadAt() {
    return lastReadAt;
  }

  public void setLastReadAt(Instant lastReadAt) {
    this.lastReadAt = lastReadAt;
  }
}
