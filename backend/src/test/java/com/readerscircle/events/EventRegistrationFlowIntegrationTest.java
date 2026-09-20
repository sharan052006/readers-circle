package com.readerscircle.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.readerscircle.auth.Role;
import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.auth.UserService;
import com.readerscircle.circles.Circle;
import com.readerscircle.circles.CircleRepository;
import com.readerscircle.circles.Membership;
import com.readerscircle.circles.MembershipRepository;
import com.readerscircle.events.dto.AttendeeDto;
import com.readerscircle.events.dto.CreateEventRequest;
import com.readerscircle.events.dto.EventDetailDto;
import com.readerscircle.events.dto.EventSummaryDto;
import com.readerscircle.events.dto.RegistrationDto;
import com.readerscircle.events.dto.UpdateEventRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class EventRegistrationFlowIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("app.jwt.secret", () -> "event-flow-integration-test-secret-at-least-32-chars");
  }

  @Autowired TestRestTemplate rest;
  @Autowired CircleRepository circleRepository;
  @Autowired MembershipRepository membershipRepository;
  @Autowired EventRepository eventRepository;
  @Autowired EventRegistrationRepository registrationRepository;
  @Autowired UserRepository userRepository;
  @Autowired UserService userService;
  @Autowired com.readerscircle.auth.AuthService authService;

  private String adminToken;
  private String organizerToken;
  private String otherOrganizerToken;
  private String memberToken;
  private String nonMemberToken;
  private UUID circleId;
  private UUID organizerId;
  private UUID memberId;

  @BeforeEach
  void seed() {
    registrationRepository.deleteAll();
    eventRepository.deleteAll();
    membershipRepository.deleteAll();
    circleRepository.deleteAll();
    userRepository.deleteAll();

    User admin = userService.create("Admin User", "admin@flow.test", "pass12345", Role.ADMIN);
    adminToken = authService.login("admin@flow.test", "pass12345").accessToken();

    User organizer = userService.create("Organizer User", "org@flow.test", "pass12345", Role.ORGANIZER);
    organizerId = organizer.getId();
    organizerToken = authService.login("org@flow.test", "pass12345").accessToken();

    User otherOrg = userService.create("Other Organizer", "otherorg@flow.test", "pass12345", Role.ORGANIZER);
    otherOrganizerToken = authService.login("otherorg@flow.test", "pass12345").accessToken();

    User member = userService.create("Member User", "member@flow.test", "pass12345", Role.READER);
    memberId = member.getId();
    memberToken = authService.login("member@flow.test", "pass12345").accessToken();

    User nonMember = userService.create("Non Member", "nonmember@flow.test", "pass12345", Role.READER);
    nonMemberToken = authService.login("nonmember@flow.test", "pass12345").accessToken();

    Circle circle = new Circle("Classic Books", "Paris", "Classic Literature Circle", organizerId);
    circle = circleRepository.save(circle);
    circleId = circle.getId();

    Membership approvedMembership = new Membership(circleId, memberId);
    approvedMembership.approve();
    membershipRepository.save(approvedMembership);
  }

  private HttpHeaders bearer(String token) {
    HttpHeaders h = new HttpHeaders();
    if (token != null) {
      h.setBearerAuth(token);
    }
    return h;
  }

  @Test
  void fullLifecycleAndGuardrails() {
    // 1. Reader cannot create event (403)
    CreateEventRequest req1 =
        new CreateEventRequest(
            "Reader Event",
            "desc",
            "Fiction",
            LocalDate.now().plusDays(5),
            LocalTime.of(19, 0),
            "Cafe",
            10,
            Instant.now().plusSeconds(86400 * 2),
            null,
            false);
    ResponseEntity<String> readerCreateResp =
        rest.exchange(
            "/api/circles/" + circleId + "/events",
            HttpMethod.POST,
            new HttpEntity<>(req1, bearer(memberToken)),
            String.class);
    assertThat(readerCreateResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    // 2. Other organizer cannot create event for this circle (403)
    ResponseEntity<String> otherOrgCreateResp =
        rest.exchange(
            "/api/circles/" + circleId + "/events",
            HttpMethod.POST,
            new HttpEntity<>(req1, bearer(otherOrganizerToken)),
            String.class);
    assertThat(otherOrgCreateResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    // 3. Organizer creates DRAFT event
    ResponseEntity<EventDetailDto> orgCreateResp =
        rest.exchange(
            "/api/circles/" + circleId + "/events",
            HttpMethod.POST,
            new HttpEntity<>(req1, bearer(organizerToken)),
            EventDetailDto.class);
    assertThat(orgCreateResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    UUID eventId = orgCreateResp.getBody().id();
    assertThat(orgCreateResp.getBody().status()).isEqualTo(EventStatus.DRAFT);

    // 4. Registration on DRAFT event is rejected (422)
    ResponseEntity<String> regOnDraftResp =
        rest.exchange(
            "/api/events/" + eventId + "/register",
            HttpMethod.POST,
            new HttpEntity<>(bearer(memberToken)),
            String.class);
    assertThat(regOnDraftResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

    // 5. Organizer updates event to PUBLISHED
    UpdateEventRequest publishReq =
        new UpdateEventRequest(
            null, null, null, null, null, null, null, null, null, EventStatus.PUBLISHED);
    ResponseEntity<EventDetailDto> publishResp =
        rest.exchange(
            "/api/events/" + eventId,
            HttpMethod.PATCH,
            new HttpEntity<>(publishReq, bearer(organizerToken)),
            EventDetailDto.class);
    assertThat(publishResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(publishResp.getBody().status()).isEqualTo(EventStatus.PUBLISHED);

    // 6. Non-member cannot register (403)
    ResponseEntity<String> nonMemberRegResp =
        rest.exchange(
            "/api/events/" + eventId + "/register",
            HttpMethod.POST,
            new HttpEntity<>(bearer(nonMemberToken)),
            String.class);
    assertThat(nonMemberRegResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    // 7. Approved member registers successfully (201)
    ResponseEntity<RegistrationDto> memberRegResp =
        rest.exchange(
            "/api/events/" + eventId + "/register",
            HttpMethod.POST,
            new HttpEntity<>(bearer(memberToken)),
            RegistrationDto.class);
    assertThat(memberRegResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(memberRegResp.getBody().status()).isEqualTo(RegistrationStatus.REGISTERED);

    // 8. Duplicate register returns 409 Conflict
    ResponseEntity<String> dupRegResp =
        rest.exchange(
            "/api/events/" + eventId + "/register",
            HttpMethod.POST,
            new HttpEntity<>(bearer(memberToken)),
            String.class);
    assertThat(dupRegResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

    // 9. Organizer views attendee list
    ResponseEntity<List<AttendeeDto>> attendeesResp =
        rest.exchange(
            "/api/events/" + eventId + "/registrations",
            HttpMethod.GET,
            new HttpEntity<>(bearer(organizerToken)),
            new ParameterizedTypeReference<>() {});
    assertThat(attendeesResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(attendeesResp.getBody()).hasSize(1);
    assertThat(attendeesResp.getBody().get(0).readerId()).isEqualTo(memberId);

    // 10. Non-organizer reader cannot view attendee list (403)
    ResponseEntity<String> readerAttendeesResp =
        rest.exchange(
            "/api/events/" + eventId + "/registrations",
            HttpMethod.GET,
            new HttpEntity<>(bearer(memberToken)),
            String.class);
    assertThat(readerAttendeesResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    // 11. Member views their registrations (/api/users/me/registrations)
    ResponseEntity<List<RegistrationDto>> myRegsResp =
        rest.exchange(
            "/api/users/me/registrations",
            HttpMethod.GET,
            new HttpEntity<>(bearer(memberToken)),
            new ParameterizedTypeReference<>() {});
    assertThat(myRegsResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(myRegsResp.getBody()).hasSize(1);
    assertThat(myRegsResp.getBody().get(0).eventId()).isEqualTo(eventId);

    // 12. Member cancels registration before deadline
    ResponseEntity<RegistrationDto> cancelRegResp =
        rest.exchange(
            "/api/events/" + eventId + "/register",
            HttpMethod.DELETE,
            new HttpEntity<>(bearer(memberToken)),
            RegistrationDto.class);
    assertThat(cancelRegResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(cancelRegResp.getBody().status()).isEqualTo(RegistrationStatus.CANCELLED);

    // 13. Member re-registers after cancellation (seat was freed)
    ResponseEntity<RegistrationDto> reRegResp =
        rest.exchange(
            "/api/events/" + eventId + "/register",
            HttpMethod.POST,
            new HttpEntity<>(bearer(memberToken)),
            RegistrationDto.class);
    assertThat(reRegResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(reRegResp.getBody().status()).isEqualTo(RegistrationStatus.REGISTERED);

    // 14. Cannot reduce capacity below active headcount
    UpdateEventRequest invalidCapReq =
        new UpdateEventRequest(
            null, null, null, null, null, null, 0, null, null, null);
    ResponseEntity<String> invalidCapResp =
        rest.exchange(
            "/api/events/" + eventId,
            HttpMethod.PATCH,
            new HttpEntity<>(invalidCapReq, bearer(organizerToken)),
            String.class);
    assertThat(invalidCapResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

    // 15. Organizer completes event
    UpdateEventRequest completeReq =
        new UpdateEventRequest(
            null, null, null, null, null, null, null, null, null, EventStatus.COMPLETED);
    ResponseEntity<EventDetailDto> completeResp =
        rest.exchange(
            "/api/events/" + eventId,
            HttpMethod.PATCH,
            new HttpEntity<>(completeReq, bearer(organizerToken)),
            EventDetailDto.class);
    assertThat(completeResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(completeResp.getBody().status()).isEqualTo(EventStatus.COMPLETED);

    // 16. Cannot transition out of COMPLETED (terminal)
    ResponseEntity<String> terminalResp =
        rest.exchange(
            "/api/events/" + eventId,
            HttpMethod.PATCH,
            new HttpEntity<>(publishReq, bearer(organizerToken)),
            String.class);
    assertThat(terminalResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }
}
