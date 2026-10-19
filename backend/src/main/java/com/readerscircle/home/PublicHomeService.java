package com.readerscircle.home;

import com.readerscircle.auth.Role;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.citations.CitationService;
import com.readerscircle.circles.Circle;
import com.readerscircle.circles.CircleRepository;
import com.readerscircle.circles.CircleStatus;
import com.readerscircle.events.Event;
import com.readerscircle.events.EventRegistrationRepository;
import com.readerscircle.events.EventRepository;
import com.readerscircle.events.EventStatus;
import com.readerscircle.events.RegistrationStatus;
import com.readerscircle.testimonials.TestimonialDto;
import com.readerscircle.testimonials.TestimonialService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PublicHomeService {

  private final UserRepository userRepository;
  private final CircleRepository circleRepository;
  private final EventRepository eventRepository;
  private final EventRegistrationRepository eventRegistrationRepository;
  private final CitationService citationService;
  private final TestimonialService testimonialService;

  public PublicHomeService(
      UserRepository userRepository,
      CircleRepository circleRepository,
      EventRepository eventRepository,
      EventRegistrationRepository eventRegistrationRepository,
      CitationService citationService,
      TestimonialService testimonialService) {
    this.userRepository = userRepository;
    this.circleRepository = circleRepository;
    this.eventRepository = eventRepository;
    this.eventRegistrationRepository = eventRegistrationRepository;
    this.citationService = citationService;
    this.testimonialService = testimonialService;
  }

  public PublicStatsDto getStats() {
    long readerCount = userRepository.countByRoleIn(List.of(Role.READER, Role.ORGANIZER));
    if (readerCount == 0) {
      readerCount = userRepository.count();
    }
    long circleCount = circleRepository.countByStatus(CircleStatus.ACTIVE);
    long eventCount =
        eventRepository.countByStatusIn(List.of(EventStatus.PUBLISHED, EventStatus.COMPLETED));
    long citationCount = citationService.getCitationCount();

    return new PublicStatsDto(readerCount, circleCount, eventCount, citationCount);
  }

  public PublicHomeDto getHomeData() {
    PublicStatsDto stats = getStats();

    LocalDate today = LocalDate.now(java.time.ZoneId.systemDefault());
    java.time.LocalTime nowTime = java.time.LocalTime.now(java.time.ZoneId.systemDefault());
    List<Event> events = eventRepository.findUpcomingLandingEvents(today, nowTime, PageRequest.of(0, 3));

    Set<UUID> circleIds = events.stream().map(Event::getCircleId).collect(Collectors.toSet());
    Map<UUID, String> circleNameMap =
        circleRepository.findAllById(circleIds).stream()
            .collect(Collectors.toMap(Circle::getId, Circle::getName, (a, b) -> a));

    List<LandingEventDto> upcoming =
        events.stream()
            .map(
                e -> {
                  String circleName = circleNameMap.getOrDefault(e.getCircleId(), "Reading Circle");
                  long registered =
                      eventRegistrationRepository.countByEventIdAndStatus(
                          e.getId(), RegistrationStatus.REGISTERED);
                  return new LandingEventDto(
                      e.getId(),
                      e.getCircleId(),
                      circleName,
                      e.getTitle(),
                      e.getReadingTopic(),
                      e.getEventDate(),
                      e.getEventTime(),
                      e.getVenue(),
                      e.getCoverImageUrl(),
                      registered,
                      e.getCapacity());
                })
            .toList();

    List<TestimonialDto> testimonials = testimonialService.getApprovedTestimonials(6);

    return new PublicHomeDto(stats, upcoming, testimonials);
  }
}
