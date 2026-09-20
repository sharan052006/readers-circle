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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "events")
public class Event {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  @Column(name = "circle_id", nullable = false)
  private UUID circleId;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "reading_topic", length = 200)
  private String readingTopic;

  @Column(name = "event_date")
  private LocalDate eventDate;

  @Column(name = "event_time")
  private LocalTime eventTime;

  @Column(length = 255)
  private String venue;

  private Integer capacity;

  @Column(name = "registration_deadline")
  private Instant registrationDeadline;

  @Column(name = "cover_image_url", length = 1000)
  private String coverImageUrl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EventStatus status = EventStatus.DRAFT;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected Event() {}

  public Event(UUID circleId, String title) {
    if (circleId == null) {
      throw new IllegalArgumentException("circleId cannot be null");
    }
    if (title == null || title.trim().isEmpty()) {
      throw new IllegalArgumentException("title cannot be empty");
    }
    this.circleId = circleId;
    this.title = title.trim();
    this.status = EventStatus.DRAFT;
    this.createdAt = Instant.now();
  }

  public void publish() {
    if (this.status != EventStatus.DRAFT) {
      throw new IllegalStateException("Only DRAFT events can be published. Current status: " + this.status);
    }
    validatePublishable();
    this.status = EventStatus.PUBLISHED;
  }

  public void validatePublishable() {
    if (title == null || title.trim().isEmpty()) {
      throw new IllegalArgumentException("Title is required to publish");
    }
    if (eventDate == null) {
      throw new IllegalArgumentException("Event date is required to publish");
    }
    if (eventTime == null) {
      throw new IllegalArgumentException("Event time is required to publish");
    }
    if (venue == null || venue.trim().isEmpty()) {
      throw new IllegalArgumentException("Venue is required to publish");
    }
    if (capacity == null || capacity <= 0) {
      throw new IllegalArgumentException("Capacity must be greater than 0 to publish");
    }
    if (registrationDeadline == null) {
      throw new IllegalArgumentException("Registration deadline is required to publish");
    }
    LocalDateTime eventDateTime = LocalDateTime.of(eventDate, eventTime);
    Instant eventInstant = eventDateTime.toInstant(ZoneOffset.UTC);
    if (registrationDeadline.isAfter(eventInstant)) {
      throw new IllegalArgumentException("Registration deadline must be before or at event time");
    }
  }

  public void cancel() {
    if (this.status == EventStatus.COMPLETED || this.status == EventStatus.CANCELLED) {
      throw new IllegalStateException("Event in terminal state cannot be cancelled. Current status: " + this.status);
    }
    this.status = EventStatus.CANCELLED;
  }

  public void complete() {
    if (this.status != EventStatus.PUBLISHED) {
      throw new IllegalStateException("Only PUBLISHED events can be marked COMPLETED. Current status: " + this.status);
    }
    this.status = EventStatus.COMPLETED;
  }

  public void assertRegistrable(Instant now) {
    if (this.status != EventStatus.PUBLISHED) {
      throw new IllegalStateException("Event is not open for registration. Status is: " + this.status);
    }
    if (registrationDeadline != null && now.isAfter(registrationDeadline)) {
      throw new IllegalArgumentException("Registration deadline has passed");
    }
  }

  public UUID getId() {
    return id;
  }

  public UUID getCircleId() {
    return circleId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    if (title == null || title.trim().isEmpty()) {
      throw new IllegalArgumentException("title cannot be empty");
    }
    this.title = title.trim();
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getReadingTopic() {
    return readingTopic;
  }

  public void setReadingTopic(String readingTopic) {
    this.readingTopic = readingTopic;
  }

  public LocalDate getEventDate() {
    return eventDate;
  }

  public void setEventDate(LocalDate eventDate) {
    this.eventDate = eventDate;
  }

  public LocalTime getEventTime() {
    return eventTime;
  }

  public void setEventTime(LocalTime eventTime) {
    this.eventTime = eventTime;
  }

  public String getVenue() {
    return venue;
  }

  public void setVenue(String venue) {
    this.venue = venue;
  }

  public Integer getCapacity() {
    return capacity;
  }

  public void setCapacity(Integer capacity) {
    if (capacity != null && capacity <= 0) {
      throw new IllegalArgumentException("capacity must be greater than 0");
    }
    this.capacity = capacity;
  }

  public Instant getRegistrationDeadline() {
    return registrationDeadline;
  }

  public void setRegistrationDeadline(Instant registrationDeadline) {
    this.registrationDeadline = registrationDeadline;
  }

  public String getCoverImageUrl() {
    return coverImageUrl;
  }

  public void setCoverImageUrl(String coverImageUrl) {
    this.coverImageUrl = coverImageUrl;
  }

  public EventStatus getStatus() {
    return status;
  }

  public void setStatus(EventStatus status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
