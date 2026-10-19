package com.readerscircle.citations;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CitationService {

  private final CitationRepository citationRepository;

  public CitationService(CitationRepository citationRepository) {
    this.citationRepository = citationRepository;
  }

  public long getCitationCount() {
    return citationRepository.count();
  }

  public List<CitationDto> getRecentCitations(int limit) {
    return citationRepository.findTop10ByOrderByCreatedAtDesc().stream()
        .limit(Math.max(1, limit))
        .map(c -> new CitationDto(
            c.getId(),
            c.getBookTitle(),
            c.getAuthor(),
            c.getQuoteSnippet(),
            c.getSourceUrl()
        ))
        .toList();
  }
}
