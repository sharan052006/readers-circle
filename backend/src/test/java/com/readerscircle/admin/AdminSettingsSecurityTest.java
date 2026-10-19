package com.readerscircle.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AdminSettingsSecurityTest.TestConfig.class)
class AdminSettingsSecurityTest {

  @Configuration
  @EnableMethodSecurity
  static class TestConfig {
    @Bean
    AdminSettingsService adminSettingsService() {
      return mock(AdminSettingsService.class);
    }

    @Bean
    AdminSettingsController adminSettingsController(AdminSettingsService adminSettingsService) {
      return new AdminSettingsController(adminSettingsService);
    }
  }

  @Autowired
  private AdminSettingsController controller;

  @Autowired
  private AdminSettingsService service;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    Map<String, Object> sampleSettings = Map.of(
        "platformName", "Reader's Circle",
        "tagline", "Build a stronger reading community");
    when(service.getSettings()).thenReturn(sampleSettings);
    when(service.updateSettings(any())).thenReturn(sampleSettings);
  }

  @Test
  void unauthenticatedUserCannotAccessGetSettings() {
    assertThatThrownBy(() -> controller.get())
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }

  @Test
  void unauthenticatedUserCannotAccessPutSettings() {
    assertThatThrownBy(() -> controller.update(Map.of("platformName", "Hacked")))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }

  @Test
  void readerRoleCannotAccessGetSettings() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            "reader", "pw", List.of(new SimpleGrantedAuthority("ROLE_READER"))));

    assertThatThrownBy(() -> controller.get())
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void readerRoleCannotAccessPutSettings() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            "reader", "pw", List.of(new SimpleGrantedAuthority("ROLE_READER"))));

    assertThatThrownBy(() -> controller.update(Map.of("platformName", "Hacked")))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void organizerRoleCannotAccessGetSettings() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            "organizer", "pw", List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER"))));

    assertThatThrownBy(() -> controller.get())
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void organizerRoleCannotAccessPutSettings() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            "organizer", "pw", List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER"))));

    assertThatThrownBy(() -> controller.update(Map.of("platformName", "Hacked")))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void adminRoleCanAccessGetSettings() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            "admin", "pw", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

    ResponseEntity<Map<String, Object>> response = controller.get();
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).containsEntry("platformName", "Reader's Circle");
  }

  @Test
  void adminRoleCanAccessPutSettings() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            "admin", "pw", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

    ResponseEntity<Map<String, Object>> response = controller.update(Map.of("platformName", "New Name"));
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).containsEntry("platformName", "Reader's Circle");
  }
}
