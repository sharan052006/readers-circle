package com.readerscircle.store;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface StoreBookRepository extends JpaRepository<StoreBook, UUID> {
  List<StoreBook> findAllByOrderByCreatedAtDesc();

  /**
   * Atomically reserve stock. Returns 1 when enough stock existed, 0 otherwise
   * (lets checkout fail fast instead of overselling).
   */
  @Modifying
  @Query("update StoreBook b set b.stockQty = b.stockQty - :qty where b.id = :id and b.stockQty >= :qty")
  int decrementStock(UUID id, int qty);
}
