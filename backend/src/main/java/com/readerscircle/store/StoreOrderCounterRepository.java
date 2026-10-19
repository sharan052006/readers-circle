package com.readerscircle.store;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface StoreOrderCounterRepository extends JpaRepository<StoreOrderCounter, Integer> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<StoreOrderCounter> findByYr(Integer yr);
}
