package com.readerscircle.admin;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("h2")
@Import(AdminSettingsService.class)
class AdminSettingsPersistenceIntegrationTest {

  @Autowired
  private AdminSettingsRepository repository;

  @Autowired
  private AdminSettingsService service;

  @Autowired
  private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    service.init();
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  void settingsPersistToDatabaseAndSurviveCacheEvictionAndInitCycle() {
    // 1. Verify default values exist in database
    Map<String, Object> initial = service.getSettings();
    assertThat(initial.get("platformName")).isEqualTo("Reader's Circle");
    assertThat(initial.get("maxCirclesPerMember")).isEqualTo(5);
    assertThat(initial.get("registrationOpen")).isEqualTo(true);

    // 2. Update settings
    service.updateSettings(Map.of(
        "platformName", "Novel Haven Community",
        "tagline", "Where stories live forever",
        "maxCirclesPerMember", 12,
        "registrationOpen", false
    ));

    entityManager.flush();
    entityManager.clear();

    // 3. Directly verify the database record via JPA repository without any in-memory service cache
    AdminSettings fromDb = repository.findById(AdminSettings.DEFAULT_ID).orElseThrow();
    assertThat(fromDb.getPlatformName()).isEqualTo("Novel Haven Community");
    assertThat(fromDb.getTagline()).isEqualTo("Where stories live forever");
    assertThat(fromDb.getMaxCirclesPerMember()).isEqualTo(12);
    assertThat(fromDb.isRegistrationOpen()).isFalse();

    // 4. Simulate application restart: trigger service.init() again
    service.init();
    entityManager.flush();
    entityManager.clear();

    // 5. Verify service.init() did NOT overwrite persisted settings with defaults
    Map<String, Object> afterRestart = service.getSettings();
    assertThat(afterRestart.get("platformName")).isEqualTo("Novel Haven Community");
    assertThat(afterRestart.get("tagline")).isEqualTo("Where stories live forever");
    assertThat(afterRestart.get("maxCirclesPerMember")).isEqualTo(12);
    assertThat(afterRestart.get("registrationOpen")).isEqualTo(false);
  }
}
