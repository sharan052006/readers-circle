package com.readerscircle.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminSettingsServiceTest {

  private AdminSettingsRepository repository;
  private AdminSettingsService service;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    repository = mock(AdminSettingsRepository.class);
    service = new AdminSettingsService(repository, objectMapper);
  }

  @Test
  void initInsertsDefaultsIfEmpty() {
    when(repository.existsById(AdminSettings.DEFAULT_ID)).thenReturn(false);

    service.init();

    verify(repository).save(any(AdminSettings.class));
  }

  @Test
  void initDoesNotOverwriteExistingSettingsOnRestart() {
    when(repository.existsById(AdminSettings.DEFAULT_ID)).thenReturn(true);

    service.init();

    verify(repository, never()).save(any(AdminSettings.class));
  }

  @Test
  void getSettingsReturnsCanonicalDefaultsWhenFirstCreated() {
    AdminSettings defaultEntity = new AdminSettings(AdminSettings.DEFAULT_ID);
    when(repository.findById(AdminSettings.DEFAULT_ID)).thenReturn(Optional.of(defaultEntity));

    Map<String, Object> settings = service.getSettings();

    assertThat(settings).isNotNull();
    assertThat(settings.get("platformName")).isEqualTo("Reader's Circle");
    assertThat(settings.get("tagline")).isEqualTo("Build a stronger reading community");
    assertThat(settings.get("communityGuidelines"))
        .isEqualTo("Be respectful, cite sources, encourage diverse literary viewpoints, and avoid spoilers without tags.");
    assertThat(settings.get("registrationOpen")).isEqualTo(true);
    assertThat(settings.get("allowMemberCircleCreation")).isEqualTo(false);
    assertThat(settings.get("maxCirclesPerMember")).isEqualTo(5);
    assertThat(settings.get("emailNotificationsEnabled")).isEqualTo(true);
  }

  @Test
  void updateSettingsPersistsUpdatedValuesAndPreservesUnmodifiedFields() {
    AdminSettings existing = new AdminSettings(AdminSettings.DEFAULT_ID);
    existing.setPlatformName("Original Name");
    existing.setTagline("Original Tagline");
    existing.setMaxCirclesPerMember(5);
    existing.setRegistrationOpen(true);

    when(repository.findById(AdminSettings.DEFAULT_ID)).thenReturn(Optional.of(existing));
    when(repository.save(any(AdminSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Map<String, Object> patch = new HashMap<>();
    patch.put("platformName", "Novel Haven");
    patch.put("maxCirclesPerMember", 10);
    patch.put("registrationOpen", false);

    Map<String, Object> updated = service.updateSettings(patch);

    assertThat(updated.get("platformName")).isEqualTo("Novel Haven");
    assertThat(updated.get("maxCirclesPerMember")).isEqualTo(10);
    assertThat(updated.get("registrationOpen")).isEqualTo(false);
    // Unmodified fields preserved
    assertThat(updated.get("tagline")).isEqualTo("Original Tagline");
    assertThat(updated.get("allowMemberCircleCreation")).isEqualTo(false);
    assertThat(updated.get("emailNotificationsEnabled")).isEqualTo(true);

    verify(repository).save(existing);
  }

  @Test
  void updateSettingsHandlesTypeCoercionSafely() {
    AdminSettings existing = new AdminSettings(AdminSettings.DEFAULT_ID);
    when(repository.findById(AdminSettings.DEFAULT_ID)).thenReturn(Optional.of(existing));
    when(repository.save(any(AdminSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Map<String, Object> patch = new HashMap<>();
    // String representations of numbers and booleans
    patch.put("maxCirclesPerMember", "15");
    patch.put("registrationOpen", "false");
    patch.put("allowMemberCircleCreation", "true");

    Map<String, Object> updated = service.updateSettings(patch);

    assertThat(updated.get("maxCirclesPerMember")).isEqualTo(15);
    assertThat(updated.get("registrationOpen")).isEqualTo(false);
    assertThat(updated.get("allowMemberCircleCreation")).isEqualTo(true);
  }

  @Test
  void updateSettingsPreservesAndReturnsCustomExtraKeys() {
    AdminSettings existing = new AdminSettings(AdminSettings.DEFAULT_ID);
    when(repository.findById(AdminSettings.DEFAULT_ID)).thenReturn(Optional.of(existing));
    when(repository.save(any(AdminSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Map<String, Object> patch = new HashMap<>();
    patch.put("platformName", "Book Nook");
    patch.put("customAnnouncement", "Maintenance scheduled for Sunday");

    Map<String, Object> updated = service.updateSettings(patch);

    assertThat(updated.get("platformName")).isEqualTo("Book Nook");
    assertThat(updated.get("customAnnouncement")).isEqualTo("Maintenance scheduled for Sunday");
    assertThat(existing.getCustomSettingsJson()).contains("Maintenance scheduled for Sunday");
  }

  @Test
  void simulatedRestartMaintainsPersistedSettings() {
    // In-memory fake repository simulating persistent database storage
    Map<String, AdminSettings> dbStore = new HashMap<>();
    AdminSettings initial = new AdminSettings(AdminSettings.DEFAULT_ID);
    initial.setPlatformName("Persistent Book Club");
    initial.setMaxCirclesPerMember(20);
    initial.setRegistrationOpen(false);
    dbStore.put(AdminSettings.DEFAULT_ID, initial);

    AdminSettingsRepository mockRepo = mock(AdminSettingsRepository.class);
    when(mockRepo.existsById(AdminSettings.DEFAULT_ID)).thenAnswer(inv -> dbStore.containsKey(AdminSettings.DEFAULT_ID));
    when(mockRepo.findById(AdminSettings.DEFAULT_ID)).thenAnswer(inv -> Optional.ofNullable(dbStore.get(AdminSettings.DEFAULT_ID)));
    when(mockRepo.save(any(AdminSettings.class))).thenAnswer(inv -> {
      AdminSettings s = inv.getArgument(0);
      dbStore.put(s.getId(), s);
      return s;
    });

    // Simulate backend restart: new service instance booting up against existing database
    AdminSettingsService restartedService = new AdminSettingsService(mockRepo, objectMapper);
    restartedService.init();

    // Verify existing settings were NOT overwritten by init
    Map<String, Object> settings = restartedService.getSettings();
    assertThat(settings.get("platformName")).isEqualTo("Persistent Book Club");
    assertThat(settings.get("maxCirclesPerMember")).isEqualTo(20);
    assertThat(settings.get("registrationOpen")).isEqualTo(false);
  }
}
