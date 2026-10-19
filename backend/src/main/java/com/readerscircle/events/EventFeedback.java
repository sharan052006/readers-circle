package com.readerscircle.events;

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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One review quote per reader per event (Flyway V20 {@code event_feedbacks}).
 * Shown on the Past tab cards ("Lively discussion on...").
 */
@Entity
@Table(
    name = "event_feedbacks",
    uniqueConstraints = @UniqueConstraint(name = "ux_event_feedback", columnNames = {"event_id", "reader_id"}))
public class EventFeedback {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "reader_id", nullable = false)
  private UUID readerId;

  @Column
  private Integer rating;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected EventFeedback() {}

  public EventFeedback(UUID eventId, UUID readerId, String content, Integer rating) {
    this.eventId = eventId;
    this.readerId = readerId;
    this.content = content;
    this.rating = rating;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public UUID getReaderId() {
    return readerId;
  }

  public Integer getRating() {
    return rating;
  }

  public void setRating(Integer rating) {
    this.rating = rating;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
