package com.readerscircle.events;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Event Chat board on the event detail screen (Flyway V20
 * {@code event_messages}). Simple REST wall; the circle WebSocket chat stays
 * per-circle.
 */
@Entity
@Table(name = "event_messages")
public class EventMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "author_id", nullable = false)
  private UUID authorId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected EventMessage() {}

  public EventMessage(UUID eventId, UUID authorId, String content) {
    this.eventId = eventId;
    this.authorId = authorId;
    this.content = content;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public UUID getAuthorId() {
    return authorId;
  }

  public String getContent() {
    return content;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
