package com.readerscircle.events;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventMessageRepository extends JpaRepository<EventMessage, UUID> {
  List<EventMessage> findByEventIdOrderByCreatedAtAsc(UUID eventId);

  long countByEventId(UUID eventId);
}
