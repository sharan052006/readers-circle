package com.readerscircle.gallery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MediaControllerSecurityTest.TestConfig.class)
class MediaControllerSecurityTest {

  @Configuration
  @EnableMethodSecurity
  static class TestConfig {
    @Bean
    FileStorageService fileStorageService() {
      return mock(FileStorageService.class);
    }

    @Bean
    MediaController mediaController(FileStorageService fileStorageService) {
      return new MediaController(fileStorageService);
    }
  }

  @Autowired
  private MediaController mediaController;

  @Autowired
  private FileStorageService fileStorageService;

  private MockMultipartFile validFile;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    validFile = new MockMultipartFile(
        "file",
        "sample.jpg",
        "image/jpeg",
        new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16});
    when(fileStorageService.storeFile(any(), eq(MediaType.PHOTO)))
        .thenReturn("/api/media/sample-uuid.jpg");
  }

  @Test
  void unauthenticatedUserCannotUploadToMedia() {
    // No authentication in SecurityContext
    assertThatThrownBy(() -> mediaController.uploadMedia(validFile))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }

  @Test
  void readerRoleCannotUploadToGenericMedia() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            "reader-user",
            "pw",
            List.of(new SimpleGrantedAuthority("ROLE_READER"))));

    assertThatThrownBy(() -> mediaController.uploadMedia(validFile))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void organizerRoleCanUploadToGenericMedia() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            "organizer-user",
            "pw",
            List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER"))));

    Map<String, String> response = mediaController.uploadMedia(validFile);
    assertThat(response).containsEntry("url", "/api/media/sample-uuid.jpg");
  }

  @Test
  void adminRoleCanUploadToGenericMedia() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            "admin-user",
            "pw",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

    Map<String, String> response = mediaController.uploadMedia(validFile);
    assertThat(response).containsEntry("url", "/api/media/sample-uuid.jpg");
  }
}
