package com.readerscircle.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.readerscircle.auth.Role;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.circles.CircleRepository;
import com.readerscircle.circles.MembershipRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class EventMessageServiceTest {

  private EventRepository events;
  private EventMessageRepository messages;
  private EventRegistrationRepository registrations;
  private CircleRepository circles;
  private MembershipRepository memberships;
  private UserRepository users;
  private EventMessageService service;

  @BeforeEach
  void setUp() {
    events = mock(EventRepository.class);
    messages = mock(EventMessageRepository.class);
    registrations = mock(EventRegistrationRepository.class);
    circles = mock(CircleRepository.class);
    memberships = mock(MembershipRepository.class);
    users = mock(UserRepository.class);

    service =
        new EventMessageService(events, messages, registrations, circles, memberships, users);
  }

  @Test
  void chatIsOpenForUpcomingOrRecentEvent() {
    Event event = new Event(UUID.randomUUID(), "Recent Event");
    event.setEventDate(LocalDate.now().plusDays(1));
    event.setEventTime(LocalTime.of(15, 0));

    assertThat(service.isChatOpen(event)).isTrue();
    assertThat(service.getChatClosesAt(event)).isNotNull();
  }

  @Test
  void chatIsClosedAfter48HoursFromScheduledEventTime() {
    Event event = new Event(UUID.randomUUID(), "Old Event");
    // 3 days ago (more than 48 hours)
    event.setEventDate(LocalDate.now().minusDays(3));
    event.setEventTime(LocalTime.of(10, 0));

    assertThat(service.isChatOpen(event)).isFalse();
  }

  @Test
  void postFailsWhenChatIsClosedAfter48Hours() {
    UUID eventId = UUID.randomUUID();
    Event event = new Event(UUID.randomUUID(), "Past Event");
    ReflectionTestUtils.setField(event, "id", eventId);
    event.setEventDate(LocalDate.now().minusDays(3));
    event.setEventTime(LocalTime.of(10, 0));

    when(events.findById(eventId)).thenReturn(Optional.of(event));

    assertThatThrownBy(
            () -> service.post(eventId, UUID.randomUUID(), Role.READER, "Hello, is anyone here?"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            ex -> {
              ResponseStatusException rse = (ResponseStatusException) ex;
              assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
              assertThat(rse.getReason()).contains("Event chat is closed");
            });
  }

  @Test
  void listIncludesChatStatusFields() {
    UUID eventId = UUID.randomUUID();
    Event event = new Event(UUID.randomUUID(), "Live Event");
    ReflectionTestUtils.setField(event, "id", eventId);
    event.setEventDate(LocalDate.now().plusDays(1));
    event.setEventTime(LocalTime.of(18, 0));

    when(events.findById(eventId)).thenReturn(Optional.of(event));
    when(messages.findByEventIdOrderByCreatedAtAsc(eventId)).thenReturn(java.util.Collections.emptyList());

    Map<String, Object> resp = service.list(eventId, UUID.randomUUID(), Role.ADMIN);

    assertThat(resp).containsKey("chatOpen");
    assertThat(resp.get("chatOpen")).isEqualTo(true);
    assertThat(resp).containsKey("chatClosesAt");
    assertThat(resp.get("chatClosesAt")).isNotNull();
  }

  @Test
  void registeredReaderCanAccessAndPostToEventChat() {
    UUID circleId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID readerId = UUID.randomUUID();

    Event event = new Event(circleId, "Book Club Meeting");
    ReflectionTestUtils.setField(event, "id", eventId);
    event.setEventDate(LocalDate.now().plusDays(1));
    event.setEventTime(LocalTime.of(18, 0));

    com.readerscircle.circles.Circle circle =
        new com.readerscircle.circles.Circle("Classics Circle", "City", "Desc", UUID.randomUUID());

    EventRegistration reg = new EventRegistration(eventId, readerId);

    when(events.findById(eventId)).thenReturn(Optional.of(event));
    when(circles.findById(circleId)).thenReturn(Optional.of(circle));
    when(registrations.findByEventIdAndReaderId(eventId, readerId)).thenReturn(Optional.of(reg));
    when(messages.findByEventIdOrderByCreatedAtAsc(eventId)).thenReturn(java.util.Collections.emptyList());
    when(messages.save(org.mockito.ArgumentMatchers.any(EventMessage.class)))
        .thenAnswer(inv -> {
          EventMessage m = inv.getArgument(0);
          ReflectionTestUtils.setField(m, "id", UUID.randomUUID());
          return m;
        });

    // List messages succeeds
    Map<String, Object> listResp = service.list(eventId, readerId, Role.READER);
    assertThat(listResp).isNotNull();

    // Post message succeeds
    Map<String, Object> postResp = service.post(eventId, readerId, Role.READER, "Hello registered friends!");
    assertThat(postResp).isNotNull();
    assertThat(postResp.get("content")).isEqualTo("Hello registered friends!");
  }

  @Test
  void unregisteredReaderWhoIsApprovedCircleMemberIsDenied() {
    UUID circleId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID readerId = UUID.randomUUID();

    Event event = new Event(circleId, "Book Club Meeting");
    ReflectionTestUtils.setField(event, "id", eventId);
    event.setEventDate(LocalDate.now().plusDays(1));
    event.setEventTime(LocalTime.of(18, 0));

    com.readerscircle.circles.Circle circle =
        new com.readerscircle.circles.Circle("Classics Circle", "City", "Desc", UUID.randomUUID());

    com.readerscircle.circles.Membership approvedMembership =
        new com.readerscircle.circles.Membership(circleId, readerId);
    approvedMembership.approve();

    when(events.findById(eventId)).thenReturn(Optional.of(event));
    when(circles.findById(circleId)).thenReturn(Optional.of(circle));
    // Reader has approved membership in the circle, but NO registration for this event
    when(memberships.findByCircleIdAndReaderId(circleId, readerId)).thenReturn(Optional.of(approvedMembership));
    when(registrations.findByEventIdAndReaderId(eventId, readerId)).thenReturn(Optional.empty());

    // Listing chat must be forbidden
    assertThatThrownBy(() -> service.list(eventId, readerId, Role.READER))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

    // Posting chat must be forbidden
    assertThatThrownBy(() -> service.post(eventId, readerId, Role.READER, "Sneaking in?"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
  }

  @Test
  void readerRegisteredForDifferentEventIsDenied() {
    UUID circleId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID otherEventId = UUID.randomUUID();
    UUID readerId = UUID.randomUUID();

    Event event = new Event(circleId, "Event A");
    ReflectionTestUtils.setField(event, "id", eventId);
    event.setEventDate(LocalDate.now().plusDays(1));
    event.setEventTime(LocalTime.of(18, 0));

    com.readerscircle.circles.Circle circle =
        new com.readerscircle.circles.Circle("Classics Circle", "City", "Desc", UUID.randomUUID());

    when(events.findById(eventId)).thenReturn(Optional.of(event));
    when(circles.findById(circleId)).thenReturn(Optional.of(circle));
    // Reader registered for otherEventId, but NOT eventId
    when(registrations.findByEventIdAndReaderId(eventId, readerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.list(eventId, readerId, Role.READER))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

    assertThatThrownBy(() -> service.post(eventId, readerId, Role.READER, "Cross event message"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
  }

  @Test
  void circleOrganizerCanAccessAndPost() {
    UUID circleId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID organizerId = UUID.randomUUID();

    Event event = new Event(circleId, "Event A");
    ReflectionTestUtils.setField(event, "id", eventId);
    event.setEventDate(LocalDate.now().plusDays(1));
    event.setEventTime(LocalTime.of(18, 0));

    com.readerscircle.circles.Circle circle =
        new com.readerscircle.circles.Circle("Classics Circle", "City", "Desc", organizerId);

    com.readerscircle.auth.User orgUser =
        new com.readerscircle.auth.User("Org", "org@x.com", "hash", Role.ORGANIZER);

    when(events.findById(eventId)).thenReturn(Optional.of(event));
    when(circles.findById(circleId)).thenReturn(Optional.of(circle));
    when(users.findById(organizerId)).thenReturn(Optional.of(orgUser));
    when(messages.findByEventIdOrderByCreatedAtAsc(eventId)).thenReturn(java.util.Collections.emptyList());
    when(messages.save(org.mockito.ArgumentMatchers.any(EventMessage.class)))
        .thenAnswer(inv -> {
          EventMessage m = inv.getArgument(0);
          ReflectionTestUtils.setField(m, "id", UUID.randomUUID());
          return m;
        });

    Map<String, Object> listResp = service.list(eventId, organizerId, Role.ORGANIZER);
    assertThat(listResp).isNotNull();

    Map<String, Object> postResp = service.post(eventId, organizerId, Role.ORGANIZER, "Welcome everyone!");
    assertThat(postResp).isNotNull();
    assertThat(postResp.get("content")).isEqualTo("Welcome everyone!");
  }

  @Test
  void adminCanAccessAndPost() {
    UUID circleId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();

    Event event = new Event(circleId, "Event A");
    ReflectionTestUtils.setField(event, "id", eventId);
    event.setEventDate(LocalDate.now().plusDays(1));
    event.setEventTime(LocalTime.of(18, 0));

    when(events.findById(eventId)).thenReturn(Optional.of(event));
    when(messages.findByEventIdOrderByCreatedAtAsc(eventId)).thenReturn(java.util.Collections.emptyList());
    when(messages.save(org.mockito.ArgumentMatchers.any(EventMessage.class)))
        .thenAnswer(inv -> {
          EventMessage m = inv.getArgument(0);
          ReflectionTestUtils.setField(m, "id", UUID.randomUUID());
          return m;
        });

    Map<String, Object> listResp = service.list(eventId, adminId, Role.ADMIN);
    assertThat(listResp).isNotNull();

    Map<String, Object> postResp = service.post(eventId, adminId, Role.ADMIN, "Admin announcement");
    assertThat(postResp).isNotNull();
    assertThat(postResp.get("content")).isEqualTo("Admin announcement");
  }
}
