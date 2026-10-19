package com.readerscircle.store;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreAddressRepository extends JpaRepository<StoreAddress, UUID> {
  List<StoreAddress> findByUserIdOrderByCreatedAtAsc(UUID userId);
}
