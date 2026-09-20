package com.readerscircle.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Task 2 gate: entity round-trip + case-insensitive email queries. */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry r) {
    // spring-boot-testcontainers is not in the offline cache; wire the URL manually.
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired UserRepository users;

  @Autowired TestEntityManager em;

  @Test
  void persistAssignsIdCreatedAtAndDefaults() {
    User saved = users.save(new User("Asha", "asha@x.com", "hash", Role.READER));
    em.flush();
    em.clear();
    User reloaded = users.findById(saved.getId()).orElseThrow();

    assertThat(reloaded.getId()).isNotNull();
    assertThat(reloaded.getCreatedAt()).isNotNull();
    assertThat(reloaded.isActive()).isTrue();
    assertThat(reloaded.getRole()).isEqualTo(Role.READER);
  }

  @Test
  void emailQueriesAreCaseInsensitive() {
    users.save(new User("Asha", "asha@x.com", "hash", Role.ORGANIZER));

    assertThat(users.findByEmailIgnoreCase("ASHA@X.COM")).isPresent();
    assertThat(users.existsByEmailIgnoreCase("Asha@X.Com")).isTrue();
    assertThat(users.existsByEmailIgnoreCase("nobody@x.com")).isFalse();
  }

  @Test
  void roleStoredAsString() {
    User saved =     users.save(new User("Bo", "bo@x.com", "hash", Role.ADMIN));

    em.flush();
    em.clear();

    assertThat(users.findById(saved.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
  }
}
