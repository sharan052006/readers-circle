package com.readerscircle.events;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalTime;
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

  List<Event> findAllByOrderByCreatedAtDesc();

  // Datetime-aware: an event leaves "upcoming" once its date+time passes,
  // not at midnight. Null time counts till end of day.
  @Query("SELECT e FROM Event e WHERE e.circleId = :circleId AND ((e.status = 'PUBLISHED' AND (e.eventDate > :today OR (e.eventDate = :today AND (e.eventTime IS NULL OR e.eventTime >= :nowTime)))) OR (e.eventDate IS NULL AND e.status = 'DRAFT')) ORDER BY e.eventDate ASC NULLS LAST, e.eventTime ASC NULLS LAST")
  List<Event> findUpcomingByCircleId(
      @Param("circleId") UUID circleId,
      @Param("today") LocalDate today,
      @Param("nowTime") LocalTime nowTime);

  @Query("SELECT e FROM Event e WHERE e.circleId = :circleId AND (e.status = 'COMPLETED' OR e.eventDate < :today OR (e.eventDate = :today AND e.eventTime IS NOT NULL AND e.eventTime < :nowTime)) ORDER BY e.eventDate DESC NULLS LAST")
  List<Event> findPastByCircleId(
      @Param("circleId") UUID circleId,
      @Param("today") LocalDate today,
      @Param("nowTime") LocalTime nowTime);

  long countByStatusIn(java.util.Collection<EventStatus> statuses);

  long countByStatus(EventStatus status);

  long countByCreatedAtAfter(java.time.Instant after);

  long countByCircleIdAndCreatedAtAfter(UUID circleId, java.time.Instant after);

  List<Event> findTop10ByOrderByCreatedAtDesc();

  List<Event> findAllByOrderByEventDateAsc();

  @Query("SELECT e FROM Event e WHERE e.status = 'PUBLISHED' AND (e.eventDate IS NULL OR e.eventDate > :today OR (e.eventDate = :today AND (e.eventTime IS NULL OR e.eventTime >= :nowTime))) ORDER BY e.eventDate ASC NULLS LAST, e.eventTime ASC NULLS LAST")
  List<Event> findUpcomingLandingEvents(
      @Param("today") LocalDate today,
      @Param("nowTime") LocalTime nowTime,
      org.springframework.data.domain.Pageable pageable);

  // Still-PUBLISHED events whose date+time has already passed: these should be
  // moved to COMPLETED so they leave "Upcoming" and land in "Past".
  @Query("SELECT e FROM Event e WHERE e.status = 'PUBLISHED' AND e.eventDate IS NOT NULL AND (e.eventDate < :today OR (e.eventDate = :today AND e.eventTime IS NOT NULL AND e.eventTime < :nowTime))")
  List<Event> findOverduePublished(
      @Param("today") LocalDate today, @Param("nowTime") LocalTime nowTime);
}
