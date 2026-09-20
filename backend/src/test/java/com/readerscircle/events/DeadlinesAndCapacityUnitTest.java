package com.readerscircle.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeadlinesAndCapacityUnitTest {

  @Test
  void assertRegistrableSucceedsWhenPublishedAndBeforeDeadline() {
    Event event = new Event(UUID.randomUUID(), "Meeting");
    event.setVenue("Room 1");
    event.setEventDate(LocalDate.now().plusDays(2));
    event.setEventTime(LocalTime.of(15, 0));
    event.setCapacity(15);
    Instant deadline = Instant.now().plusSeconds(3600);
    event.setRegistrationDeadline(deadline);
    event.publish();

    // Checked before deadline
    event.assertRegistrable(deadline.minusSeconds(60));
  }

  @Test
  void assertRegistrableFailsWhenPastDeadline() {
    Event event = new Event(UUID.randomUUID(), "Meeting");
    event.setVenue("Room 1");
    event.setEventDate(LocalDate.now().plusDays(2));
    event.setEventTime(LocalTime.of(15, 0));
    event.setCapacity(15);
    Instant deadline = Instant.now().plusSeconds(3600);
    event.setRegistrationDeadline(deadline);
    event.publish();

    // Checked after deadline
    assertThatThrownBy(() -> event.assertRegistrable(deadline.plusSeconds(10)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Registration deadline has passed");
  }

  @Test
  void assertRegistrableFailsWhenNotPublished() {
    Event draft = new Event(UUID.randomUUID(), "Draft");
    assertThatThrownBy(() -> draft.assertRegistrable(Instant.now()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Event is not open for registration");

    draft.setVenue("Room 1");
    draft.setEventDate(LocalDate.now().plusDays(2));
    draft.setEventTime(LocalTime.of(15, 0));
    draft.setCapacity(15);
    draft.setRegistrationDeadline(Instant.now().plusSeconds(3600));
    draft.cancel();

    assertThatThrownBy(() -> draft.assertRegistrable(Instant.now()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Event is not open for registration");
  }

  @Test
  void capacityMustBePositive() {
    Event event = new Event(UUID.randomUUID(), "Meeting");
    assertThatThrownBy(() -> event.setCapacity(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> event.setCapacity(-5))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
