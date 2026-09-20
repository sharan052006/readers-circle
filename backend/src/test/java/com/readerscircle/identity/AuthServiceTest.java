package com.readerscircle.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceTest {

  @Autowired AuthService auth;
  @Autowired UserRepository users;

  @Test
  void registerLoginRefreshRotation() {
    String email = "reader" + System.nanoTime() + "@test.com";
    AuthResponse reg = auth.registerReader(new RegisterRequest("R", email, "password123"));
    assertThat(reg.accessToken()).isNotBlank();
    assertThat(reg.role()).isEqualTo("READER");

    AuthResponse login = auth.login(new LoginRequest(email, "password123"));
    assertThat(login.userId()).isEqualTo(reg.userId());

    AuthResponse rotated = auth.refresh(login.refreshToken());
    assertThat(rotated.accessToken()).isNotBlank();

    assertThatThrownBy(() -> auth.refresh(login.refreshToken()))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void duplicateRegister409() {
    String email = "dup" + System.nanoTime() + "@test.com";
    auth.registerReader(new RegisterRequest("A", email, "password123"));
    assertThatThrownBy(() -> auth.registerReader(new RegisterRequest("B", email, "password123")))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void invalidLogin401() {
    assertThatThrownBy(() -> auth.login(new LoginRequest("nope@test.com", "wrong")))
        .isInstanceOf(ResponseStatusException.class);
  }
}
