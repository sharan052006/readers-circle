package com.readerscircle.events;

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

@Entity
@Table(name = "event_registrations")
public class EventRegistration {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "reader_id", nullable = false)
  private UUID readerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RegistrationStatus status = RegistrationStatus.REGISTERED;

  @Column(name = "registered_at", nullable = false, updatable = false)
  private Instant registeredAt = Instant.now();

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  protected EventRegistration() {}

  public EventRegistration(UUID eventId, UUID readerId) {
    if (eventId == null || readerId == null) {
      throw new IllegalArgumentException("eventId and readerId cannot be null");
    }
    this.eventId = eventId;
    this.readerId = readerId;
    this.status = RegistrationStatus.REGISTERED;
    this.registeredAt = Instant.now();
  }

  public void cancel() {
    if (this.status == RegistrationStatus.CANCELLED) {
      throw new IllegalStateException("Registration is already cancelled");
    }
    this.status = RegistrationStatus.CANCELLED;
    this.cancelledAt = Instant.now();
  }

  public void reactivate() {
    this.status = RegistrationStatus.REGISTERED;
    this.registeredAt = Instant.now();
    this.cancelledAt = null;
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

  public RegistrationStatus getStatus() {
    return status;
  }

  public Instant getRegisteredAt() {
    return registeredAt;
  }

  public Instant getCancelledAt() {
    return cancelledAt;
  }
}
