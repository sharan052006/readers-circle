package com.readerscircle.auth;

import com.readerscircle.auth.dto.UserDto;
import com.readerscircle.gallery.FileStorageService;
import com.readerscircle.gallery.MediaType;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users/me")
@PreAuthorize("isAuthenticated()")
public class CurrentUserController {

  public record UpdateProfileRequest(
      String name,
      String email,
      String bio,
      String profilePictureUrl,
      String city,
      java.util.List<String> interests) {}

  public record ChangePasswordRequest(
      String currentPassword, @Size(min = 8, max = 100) String newPassword) {}

  private final UserService userService;
  private final FileStorageService fileStorageService;

  public CurrentUserController(UserService userService, FileStorageService fileStorageService) {
    this.userService = userService;
    this.fileStorageService = fileStorageService;
  }

  @GetMapping
  public UserDto getCurrentUser(Authentication authentication) {
    return userService.getCurrentUser(authentication);
  }

  @PatchMapping
  public UserDto updateProfile(
      Authentication authentication, @RequestBody UpdateProfileRequest request) {
    return userService.updateProfile(
        authentication, request.name(), request.email(), request.bio(), request.profilePictureUrl(),
        request.city(), request.interests());
  }

  /** Admin Profile → Change Photo (JPG/PNG/WEBP, served from /api/media). */
  @PostMapping(value = "/photo", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
  public UserDto uploadPhoto(
      Authentication authentication, @RequestParam("file") MultipartFile file) {
    String url = fileStorageService.storeFile(file, MediaType.PHOTO);
    return userService.updateProfile(authentication, null, null, null, url);
  }

  /** Security Settings → Change password. */
  @PostMapping("/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(
      Authentication authentication, @RequestBody ChangePasswordRequest request) {
    if (request == null
        || request.currentPassword() == null
        || request.newPassword() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current and new password are required");
    }
    userService.changePassword(authentication, request.currentPassword(), request.newPassword());
  }
}