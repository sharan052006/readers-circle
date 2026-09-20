package com.readerscircle.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Task 6 gate: 10-thread registration race → exactly 1 row, 9 × 409 (never 500, never 2 rows).
 */
@SpringBootTest
@Testcontainers
class ConcurrentRegisterTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("app.jwt.secret", () -> "concurrent-test-secret-at-least-32-chars");
  }

  @Autowired AuthService auth;
  @Autowired UserRepository users;

  @Test
  void concurrentRegisterYieldsOneRow() throws Exception {
    int threads = 10;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      CountDownLatch ready = new CountDownLatch(threads);
      CountDownLatch go = new CountDownLatch(1);
      List<Future<Boolean>> futures = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        final int n = i;
        futures.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  if (!go.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("start gate timed out");
                  }
                  try {
                    auth.register("Race" + n, "race@x.com", "password123");
                    return true;
                  } catch (ResponseStatusException e) {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    return false;
                  }
                }));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      go.countDown();

      long wins = 0;
      for (Future<Boolean> f : futures) {
        if (f.get(30, TimeUnit.SECONDS)) {
          wins++;
        }
      }
      assertThat(wins).isEqualTo(1);
      assertThat(users.count()).isEqualTo(1);
      assertThat(users.findByEmailIgnoreCase("RACE@X.COM")).isPresent();
    } finally {
      pool.shutdownNow();
    }
  }
}
