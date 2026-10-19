package com.readerscircle.store;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreOrderItemRepository extends JpaRepository<StoreOrderItem, UUID> {
  List<StoreOrderItem> findByOrderId(UUID orderId);
}
