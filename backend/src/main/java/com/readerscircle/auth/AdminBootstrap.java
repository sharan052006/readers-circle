package com.readerscircle.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Ensures an initial administrator account exists when the application starts
 * in any deployment environment.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${app.admin.email:admin@readerscircle.com}")
  private String adminEmail;

  @Value("${app.admin.password:Password123!}")
  private String adminPassword;

  @Value("${app.admin.name:Admin User}")
  private String adminName;

  public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public void run(ApplicationArguments args) {
    // Single inbuilt platform admin. No second seed account.
    if (!userRepository.existsByEmailIgnoreCase(adminEmail)) {
      log.info("Bootstrapping default administrator: {}", adminEmail);
      User admin =
          new User(
              adminName,
              adminEmail.toLowerCase().trim(),
              passwordEncoder.encode(adminPassword),
              Role.ADMIN);
      userRepository.save(admin);
      log.info("Default administrator account created successfully.");
    }
  }
}
