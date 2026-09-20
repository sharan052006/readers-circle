package com.readerscircle.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventStatusTransitionTest {

  @Test
  void draftEventCanBePublishedWhenValid() {
    Event event = new Event(UUID.randomUUID(), "Book Club Launch");
    event.setVenue("Central Library");
    event.setEventDate(LocalDate.now().plusDays(5));
    event.setEventTime(LocalTime.of(18, 30));
    event.setCapacity(20);
    event.setRegistrationDeadline(Instant.now().plusSeconds(86400));

    event.publish();
    assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
  }

  @Test
  void cannotPublishWithoutRequiredFields() {
    Event event = new Event(UUID.randomUUID(), "Incomplete Draft");
    assertThatThrownBy(event::publish)
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void cannotPublishWithDeadlineAfterEventTime() {
    Event event = new Event(UUID.randomUUID(), "Late Deadline Event");
    event.setVenue("Central Library");
    LocalDate eventDate = LocalDate.now().plusDays(2);
    event.setEventDate(eventDate);
    event.setEventTime(LocalTime.of(14, 0));
    event.setCapacity(10);
    // Deadline after event date
    event.setRegistrationDeadline(Instant.now().plusSeconds(86400 * 5));

    assertThatThrownBy(event::publish)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Registration deadline must be before or at event time");
  }

  @Test
  void publishedEventCanBeCompletedOrCancelled() {
    Event event = new Event(UUID.randomUUID(), "Event");
    event.setVenue("Venue");
    event.setEventDate(LocalDate.now().plusDays(1));
    event.setEventTime(LocalTime.of(10, 0));
    event.setCapacity(10);
    event.setRegistrationDeadline(Instant.now().plusSeconds(3600));
    event.publish();

    Event eventToCancel = new Event(UUID.randomUUID(), "Event 2");
    eventToCancel.setVenue("Venue");
    eventToCancel.setEventDate(LocalDate.now().plusDays(1));
    eventToCancel.setEventTime(LocalTime.of(10, 0));
    eventToCancel.setCapacity(10);
    eventToCancel.setRegistrationDeadline(Instant.now().plusSeconds(3600));
    eventToCancel.publish();

    event.complete();
    assertThat(event.getStatus()).isEqualTo(EventStatus.COMPLETED);

    eventToCancel.cancel();
    assertThat(eventToCancel.getStatus()).isEqualTo(EventStatus.CANCELLED);
  }

  @Test
  void completedAndCancelledAreTerminal() {
    Event event = new Event(UUID.randomUUID(), "Completed Event");
    event.setVenue("Venue");
    event.setEventDate(LocalDate.now().plusDays(1));
    event.setEventTime(LocalTime.of(10, 0));
    event.setCapacity(10);
    event.setRegistrationDeadline(Instant.now().plusSeconds(3600));
    event.publish();
    event.complete();

    assertThatThrownBy(event::cancel)
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(event::publish)
        .isInstanceOf(IllegalStateException.class);

    Event cancelled = new Event(UUID.randomUUID(), "Cancelled Event");
    cancelled.cancel();
    assertThatThrownBy(cancelled::publish)
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(cancelled::complete)
        .isInstanceOf(IllegalStateException.class);
  }
}
