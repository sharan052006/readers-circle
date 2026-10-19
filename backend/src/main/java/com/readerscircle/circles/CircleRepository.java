package com.readerscircle.circles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CircleRepository extends JpaRepository<Circle, UUID> {

  List<Circle> findByCityIgnoreCaseAndStatus(String city, CircleStatus status);

  List<Circle> findAllByStatus(CircleStatus status);

  Optional<Circle> findByIdAndStatus(UUID id, CircleStatus status);

  List<Circle> findByOrganizerId(UUID organizerId);

  Optional<Circle> findByNameIgnoreCase(String name);

  long countByStatus(CircleStatus status);

  long countByCreatedAtAfter(java.time.OffsetDateTime after);

  List<Circle> findAllByOrderByCreatedAtDesc();

  List<Circle> findTop5ByOrderByCreatedAtDesc();

  List<Circle> findTop10ByOrderByUpdatedAtDesc();
}
