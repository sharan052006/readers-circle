package com.readerscircle.store;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreCartItemRepository extends JpaRepository<StoreCartItem, UUID> {
  List<StoreCartItem> findByUserIdOrderByCreatedAtAsc(UUID userId);

  Optional<StoreCartItem> findByUserIdAndBookId(UUID userId, UUID bookId);

  void deleteByUserId(UUID userId);

  void deleteByBookId(UUID bookId);
}
