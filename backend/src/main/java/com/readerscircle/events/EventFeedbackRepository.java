package com.readerscircle.events;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventFeedbackRepository extends JpaRepository<EventFeedback, UUID> {
  List<EventFeedback> findByEventIdOrderByCreatedAtDesc(UUID eventId);

  Optional<EventFeedback> findByEventIdAndReaderId(UUID eventId, UUID readerId);

  long countByEventId(UUID eventId);
}
