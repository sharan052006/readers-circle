package com.readerscircle.gallery;

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
@Table(name = "event_gallery_items")
public class GalleryItem {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "uploaded_by", nullable = false)
  private UUID uploadedBy;

  @Enumerated(EnumType.STRING)
  @Column(name = "media_type", nullable = false, length = 20)
  private MediaType mediaType;

  @Column(name = "media_url", nullable = false, length = 1000)
  private String mediaUrl;

  @Column(length = 255)
  private String caption;

  @Column(name = "uploaded_at", nullable = false, updatable = false)
  private Instant uploadedAt = Instant.now();

  protected GalleryItem() {}

  public GalleryItem(UUID eventId, UUID uploadedBy, MediaType mediaType, String mediaUrl, String caption) {
    if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
    if (uploadedBy == null) throw new IllegalArgumentException("uploadedBy cannot be null");
    if (mediaType == null) throw new IllegalArgumentException("mediaType cannot be null");
    if (mediaUrl == null || mediaUrl.trim().isEmpty()) {
      throw new IllegalArgumentException("mediaUrl cannot be empty");
    }
    this.eventId = eventId;
    this.uploadedBy = uploadedBy;
    this.mediaType = mediaType;
    this.mediaUrl = mediaUrl.trim();
    this.caption = caption != null && !caption.trim().isEmpty() ? caption.trim() : null;
    this.uploadedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public UUID getUploadedBy() {
    return uploadedBy;
  }

  public MediaType getMediaType() {
    return mediaType;
  }

  public String getMediaUrl() {
    return mediaUrl;
  }

  public String getCaption() {
    return caption;
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }
}
