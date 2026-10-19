package com.readerscircle.circles;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Maps 1:1 to Flyway V2 {@code circles} table. */
@Entity
@Table(name = "circles")
public class Circle {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 150)
  private String name;

  @Column(nullable = false, length = 100)
  private String city;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column(name = "organizer_id", nullable = false)
  private UUID organizerId;

  @Column(name = "cover_image_url", columnDefinition = "TEXT")
  private String coverImageUrl;

  @Column(length = 100)
  private String category;

  @Column(length = 1000)
  private String tags;

  @Column(nullable = false, columnDefinition = "boolean default false")
  private boolean hidden = false;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CircleStatus status = CircleStatus.ACTIVE;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;

  protected Circle() {}

  public Circle(String name, String city, String description, UUID organizerId) {
    this(name, city, description, organizerId, null);
  }

  public Circle(String name, String city, String description, UUID organizerId, String coverImageUrl) {
    this(name, city, description, organizerId, coverImageUrl, null);
  }

  public Circle(
      String name, String city, String description, UUID organizerId, String coverImageUrl, String category) {
    this.name = name;
    this.city = city;
    this.description = description;
    this.organizerId = organizerId;
    this.coverImageUrl = coverImageUrl;
    this.category = category;
    this.status = CircleStatus.ACTIVE;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public UUID getOrganizerId() {
    return organizerId;
  }

  public void setOrganizerId(UUID organizerId) {
    this.organizerId = organizerId;
  }

  public CircleStatus getStatus() {
    return status;
  }

  public void setStatus(CircleStatus status) {
    this.status = status;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public String getCoverImageUrl() {
    return coverImageUrl;
  }

  public void setCoverImageUrl(String coverImageUrl) {
    this.coverImageUrl = coverImageUrl;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getTags() {
    return tags;
  }

  public void setTags(String tags) {
    this.tags = tags;
  }

  /** Hidden circles (e.g. Organisers Lounge) never appear to readers. */
  public boolean isHidden() {
    return hidden;
  }

  public void setHidden(boolean hidden) {
    this.hidden = hidden;
  }

  public boolean isActive() {
    return this.status == CircleStatus.ACTIVE;
  }
}
