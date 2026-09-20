package com.readerscircle.events;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT e FROM Event e WHERE e.id = :id")
  Optional<Event> findByIdForUpdate(@Param("id") UUID id);

  List<Event> findByCircleIdOrderByCreatedAtDesc(UUID circleId);

  @Query("SELECT e FROM Event e WHERE e.circleId = :circleId AND ((e.eventDate >= :today AND e.status = 'PUBLISHED') OR (e.eventDate IS NULL AND e.status = 'DRAFT')) ORDER BY e.eventDate ASC NULLS LAST, e.eventTime ASC NULLS LAST")
  List<Event> findUpcomingByCircleId(@Param("circleId") UUID circleId, @Param("today") LocalDate today);

  @Query("SELECT e FROM Event e WHERE e.circleId = :circleId AND (e.eventDate < :today OR e.status = 'COMPLETED') ORDER BY e.eventDate DESC NULLS LAST")
  List<Event> findPastByCircleId(@Param("circleId") UUID circleId, @Param("today") LocalDate today);
}
