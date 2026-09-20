package com.readerscircle.gallery;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GalleryItemRepository extends JpaRepository<GalleryItem, UUID> {

  List<GalleryItem> findByEventIdOrderByUploadedAtAsc(UUID eventId);

  long countByEventId(UUID eventId);
}
