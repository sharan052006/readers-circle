package com.readerscircle.circles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CircleOrganizerRepository extends JpaRepository<CircleOrganizer, UUID> {

  List<CircleOrganizer> findByCircleIdOrderByAddedAtAsc(UUID circleId);

  Optional<CircleOrganizer> findByCircleIdAndUserId(UUID circleId, UUID userId);

  long countByCircleIdAndRemovedAtIsNull(UUID circleId);
}
