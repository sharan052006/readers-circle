package com.readerscircle.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

  @Autowired UserRepository users;

  @Test
  void persistsWithDefaults() {
    User saved = users.save(new User("Asha", "Asha@Example.com ", "hash", Role.READER));
    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getEmail()).isEqualTo("asha@example.com");
    assertThat(saved.getRole()).isEqualTo(Role.READER);
    assertThat(saved.isDeactivated()).isFalse();
    assertThat(saved.getCreatedAt()).isNotNull();
  }

  @Test
  void duplicateEmailRejected() {
    users.saveAndFlush(new User("One", "dup@example.com", "h1", Role.READER));
    assertThatThrownBy(() -> users.saveAndFlush(new User("Two", "DUP@example.com", "h2", Role.READER)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void findByEmailIgnoreCase() {
    users.save(new User("B", "Case@Test.com", "h", Role.ORGANIZER));
    assertThat(users.findByEmailIgnoreCase("case@test.COM")).isPresent();
    assertThat(users.existsByEmailIgnoreCase("CASE@test.com")).isTrue();
  }
}
