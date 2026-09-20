package com.readerscircle.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdmin implements CommandLineRunner {

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final String email;
  private final String password;
  private final String name;

  public BootstrapAdmin(
      UserRepository users,
      PasswordEncoder encoder,
      @Value("${app.admin-email:admin@readers.local}") String email,
      @Value("${app.admin-password:ChangeMe123!}") String password,
      @Value("${app.admin-name:Platform Admin}") String name) {
    this.users = users;
    this.encoder = encoder;
    this.email = email;
    this.password = password;
    this.name = name;
  }

  @Override
  public void run(String... args) {
    String normalized = User.normalizeEmail(email);
    if (users.existsByEmailIgnoreCase(normalized)) {
      return;
    }
    users.save(new User(name, normalized, encoder.encode(password), Role.ADMIN));
  }
}
