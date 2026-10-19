package com.readerscircle.chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatReadStateRepository extends JpaRepository<ChatReadState, UUID> {

  Optional<ChatReadState> findByCircleIdAndReaderId(UUID circleId, UUID readerId);

  List<ChatReadState> findByReaderId(UUID readerId);
}
