package com.readerscircle.circles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CircleRepository extends JpaRepository<Circle, UUID> {

  List<Circle> findByCityIgnoreCaseAndStatus(String city, CircleStatus status);

  List<Circle> findAllByStatus(CircleStatus status);

  Optional<Circle> findByIdAndStatus(UUID id, CircleStatus status);
}
