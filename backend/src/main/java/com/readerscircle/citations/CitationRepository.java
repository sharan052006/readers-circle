package com.readerscircle.citations;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CitationRepository extends JpaRepository<Citation, UUID> {

  long count();

  List<Citation> findTop10ByOrderByCreatedAtDesc();
}
