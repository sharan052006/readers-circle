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
import com.readerscircle.circles.MembershipStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
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
class CapacityConcurrencyTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("app.jwt.secret", () -> "capacity-concurrency-test-secret-at-least-32-chars");
  }

  @Autowired TestRestTemplate rest;
  @Autowired CircleRepository circleRepository;
  @Autowired MembershipRepository membershipRepository;
  @Autowired EventRepository eventRepository;
  @Autowired EventRegistrationRepository registrationRepository;
  @Autowired UserRepository userRepository;
  @Autowired UserService userService;
  @Autowired com.readerscircle.auth.AuthService authService;

  private UUID circleId;
  private UUID eventId;
  private final List<String> readerTokens = new ArrayList<>();

  @BeforeEach
  void seed() {
    registrationRepository.deleteAll();
    eventRepository.deleteAll();
    membershipRepository.deleteAll();
    circleRepository.deleteAll();
    userRepository.deleteAll();
    readerTokens.clear();

    User organizer = userService.create("Organizer One", "org@concurrency.test", "pass12345", Role.ORGANIZER);

    Circle circle = new Circle("Concurrency Circle", "London", "Test Circle", organizer.getId());
    circle = circleRepository.save(circle);
    circleId = circle.getId();

    Event event = new Event(circleId, "Concurrency Book Discussion");
    event.setVenue("Room 101");
    event.setEventDate(LocalDate.now().plusDays(7));
    event.setEventTime(LocalTime.of(18, 0));
    event.setCapacity(5); // Capacity is exactly 5
    event.setRegistrationDeadline(Instant.now().plusSeconds(86400 * 3));
    event.publish();
    event = eventRepository.save(event);
    eventId = event.getId();

    // Create 20 readers who are approved members of the circle
    for (int i = 0; i < 20; i++) {
      User reader = userService.create("Reader " + i, "reader" + i + "@concurrency.test", "pass12345", Role.READER);
      Membership membership = new Membership(circleId, reader.getId());
      membership.approve();
      membershipRepository.save(membership);

      String token = authService.login("reader" + i + "@concurrency.test", "pass12345").accessToken();
      readerTokens.add(token);
    }
  }

  @Test
  void twentyConcurrentRegistrationsForCapacityFiveYieldsExactlyFiveSuccesses() throws Exception {
    int threadCount = 20;
    ExecutorService pool = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startGate = new CountDownLatch(1);
    CountDownLatch doneGate = new CountDownLatch(threadCount);
    List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

    for (int i = 0; i < threadCount; i++) {
      final String token = readerTokens.get(i);
      pool.submit(() -> {
        try {
          startGate.await();
          HttpHeaders headers = new HttpHeaders();
          headers.setBearerAuth(token);
          ResponseEntity<String> resp =
              rest.exchange(
                  "/api/events/" + eventId + "/register",
                  HttpMethod.POST,
                  new HttpEntity<>(headers),
                  String.class);
          statusCodes.add(resp.getStatusCode().value());
        } catch (Exception e) {
          statusCodes.add(500);
        } finally {
          doneGate.countDown();
        }
      });
    }

    startGate.countDown();
    doneGate.await();
    pool.shutdown();

    long createdCount = statusCodes.stream().filter(c -> c == HttpStatus.CREATED.value()).count();
    long conflictCount = statusCodes.stream().filter(c -> c == HttpStatus.CONFLICT.value()).count();

    assertThat(createdCount).as("Exactly 5 registrations should succeed").isEqualTo(5);
    assertThat(conflictCount).as("Exactly 15 registrations should receive 409 Conflict").isEqualTo(15);

    long activeCountInDb =
        registrationRepository.countByEventIdAndStatus(eventId, RegistrationStatus.REGISTERED);
    assertThat(activeCountInDb).as("Database active registrations must strictly equal capacity of 5").isEqualTo(5);
  }
}
