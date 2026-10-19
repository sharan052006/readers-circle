package com.readerscircle.testimonials;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TestimonialService {

  private final TestimonialRepository testimonialRepository;

  public TestimonialService(TestimonialRepository testimonialRepository) {
    this.testimonialRepository = testimonialRepository;
  }

  public List<TestimonialDto> getApprovedTestimonials(int limit) {
    return testimonialRepository.findByApprovedTrueOrderByCreatedAtDesc().stream()
        .limit(Math.max(1, limit))
        .map(t -> new TestimonialDto(
            t.getId(),
            t.getMemberName(),
            t.getMemberRole(),
            t.getProfilePhotoUrl(),
            t.getCircleName(),
            t.getQuote(),
            t.getRating()
        ))
        .toList();
  }
}
