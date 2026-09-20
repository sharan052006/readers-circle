package com.readerscircle.events;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRegistrationRepository extends JpaRepository<EventRegistration, UUID> {

  long countByEventIdAndStatus(UUID eventId, RegistrationStatus status);

  Optional<EventRegistration> findByEventIdAndReaderId(UUID eventId, UUID readerId);

  List<EventRegistration> findByEventIdAndStatusOrderByRegisteredAtAsc(UUID eventId, RegistrationStatus status);

  List<EventRegistration> findByReaderIdAndStatusOrderByRegisteredAtDesc(UUID readerId, RegistrationStatus status);

  @Query("SELECT r FROM EventRegistration r WHERE r.readerId = :readerId ORDER BY r.registeredAt DESC")
  List<EventRegistration> findAllByReaderId(@Param("readerId") UUID readerId);
}
