package com.readerscircle.events;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps event lifecycle in sync with the clock: as soon as an event's scheduled
 * date+time passes, a still-PUBLISHED event is moved to COMPLETED so it drops
 * out of every role's "Upcoming" list and shows up under "Past" automatically.
 */
@Service
public class EventLifecycleService {

  private static final Logger log = LoggerFactory.getLogger(EventLifecycleService.class);

  private final EventRepository eventRepository;
  private final Clock clock;

  public EventLifecycleService(EventRepository eventRepository, Clock clock) {
    this.eventRepository = eventRepository;
    this.clock = clock;
  }

  /** Safety-net sweep so events move even when nobody is browsing the page. */
  @Scheduled(fixedDelayString = "${app.events.auto-complete-interval-ms:30000}", initialDelayString = "5000")
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void scheduledSweep() {
    completeOverdueEvents();
  }

  /**
   * Idempotently completes every PUBLISHED event whose date+time has passed and
   * returns how many were moved. Safe to call from read paths.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int completeOverdueEvents() {
    LocalDate today = LocalDate.now(clock);
    LocalTime now = LocalTime.now(clock);
    List<Event> overdue = eventRepository.findOverduePublished(today, now);
    int moved = 0;
    for (Event event : overdue) {
      try {
        event.complete();
        moved++;
      } catch (IllegalStateException ignored) {
        // Raced another sweep or already terminal — nothing to do.
      }
    }
    if (moved > 0) {
      eventRepository.saveAll(overdue);
      log.info("Auto-completed {} past event(s)", moved);
    }
    return moved;
  }
}
