package com.readerscircle.store;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreOrderRepository extends JpaRepository<StoreOrder, UUID> {
  List<StoreOrder> findByUserIdOrderByCreatedAtDesc(UUID userId);

  List<StoreOrder> findAllByOrderByCreatedAtDesc();

  long countByStatus(OrderStatus status);
}
