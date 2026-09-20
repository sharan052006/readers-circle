package com.readerscircle.circles;

import static org.assertj.core.api.Assertions.assertThat;

import com.readerscircle.auth.Role;
import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.auth.UserService;
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
class ConcurrentJoinRequestTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("app.jwt.secret", () -> "concurrent-join-request-test-secret-at-least-32-chars");
  }

  @Autowired TestRestTemplate rest;
  @Autowired CircleRepository circleRepository;
  @Autowired MembershipRepository membershipRepository;
  @Autowired UserRepository userRepository;
  @Autowired UserService userService;
  @Autowired com.readerscircle.auth.AuthService authService;

  private UUID circleId;
  private String readerToken;
  private UUID readerId;

  @BeforeEach
  void seed() {
    membershipRepository.deleteAll();
    circleRepository.deleteAll();
    userRepository.deleteAll();

    User organizer = userService.create("Organizer", "org@test.com", "pass123456", Role.ORGANIZER);
    User reader = userService.create("Reader", "reader@test.com", "pass123456", Role.READER);

    readerId = reader.getId();
    readerToken = authService.login("reader@test.com", "pass123456").accessToken();

    Circle circle = circleRepository.save(new Circle("Fantasy Club", "Barisal", "Fantasy books", organizer.getId()));
    circleId = circle.getId();
  }

  @Test
  void concurrentJoinRequestsProduceExactlyOneRowAndConflictOnDuplicate() throws Exception {
    int threads = 6;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    List<HttpStatus> statuses = Collections.synchronizedList(new ArrayList<>());

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(readerToken);

    for (int i = 0; i < threads; i++) {
      executor.submit(() -> {
        ready.countDown();
        try {
          start.await();
          ResponseEntity<String> res =
              rest.exchange(
                  "/api/circles/" + circleId + "/join-requests",
                  HttpMethod.POST,
                  new HttpEntity<>(null, headers),
                  String.class);
          statuses.add(HttpStatus.valueOf(res.getStatusCode().value()));
        } catch (InterruptedException ignored) {
        }
      });
    }

    ready.await();
    start.countDown();
    executor.shutdown();
    while (!executor.isTerminated()) {
      Thread.sleep(20);
    }

    long successCount = statuses.stream().filter(s -> s == HttpStatus.CREATED).count();
    long conflictCount = statuses.stream().filter(s -> s == HttpStatus.CONFLICT).count();

    assertThat(successCount).isEqualTo(1);
    assertThat(conflictCount).isEqualTo(threads - 1);
    assertThat(membershipRepository.findAll().stream().filter(m -> m.getReaderId().equals(readerId)).count())
        .isEqualTo(1);
  }
}
