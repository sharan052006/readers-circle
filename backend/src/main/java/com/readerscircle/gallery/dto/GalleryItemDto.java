package com.readerscircle.gallery.dto;

import com.readerscircle.gallery.MediaType;
import java.time.Instant;
import java.util.UUID;

public record GalleryItemDto(
    UUID id,
    UUID eventId,
    UUID uploadedBy,
    String uploaderName,
    MediaType mediaType,
    String mediaUrl,
    String caption,
    Instant uploadedAt
) {}
