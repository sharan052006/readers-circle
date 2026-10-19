package com.readerscircle.notifications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** Notification inbox row backing the reader top-bar bell (Flyway V18). */
@Entity
@Table(name = "notifications")
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 60)
  private String type = "GENERAL";

  @Column(nullable = false, length = 255)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String body;

  @Column(length = 1000)
  private String link;

  @Column(name = "is_read", nullable = false)
  private boolean read = false;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Notification() {}

  public Notification(UUID userId, String type, String title, String body, String link) {
    this.userId = userId;
    this.type = type == null || type.isBlank() ? "GENERAL" : type.trim().toUpperCase();
    this.title = title;
    this.body = body;
    this.link = link;
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public String getType() { return type; }
  public String getTitle() { return title; }
  public String getBody() { return body; }
  public String getLink() { return link; }
  public boolean isRead() { return read; }
  public void markRead() { this.read = true; }
  public Instant getCreatedAt() { return createdAt; }
}
