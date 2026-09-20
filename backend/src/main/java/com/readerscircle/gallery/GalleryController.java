package com.readerscircle.gallery;

import com.readerscircle.auth.Role;
import com.readerscircle.gallery.dto.AddGalleryMediaRequest;
import com.readerscircle.gallery.dto.GalleryItemDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class GalleryController {

  private final GalleryService galleryService;

  public GalleryController(GalleryService galleryService) {
    this.galleryService = galleryService;
  }

  @PostMapping("/api/events/{id}/gallery")
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.CREATED)
  public GalleryItemDto addMedia(
      @PathVariable UUID id,
      @Valid @RequestBody AddGalleryMediaRequest request,
      Authentication authentication) {
    UUID callerId = getUserId(authentication);
    Role callerRole = getUserRole(authentication);
    return galleryService.addMedia(id, callerId, callerRole, request);
  }

  @GetMapping("/api/events/{id}/gallery")
  @PreAuthorize("isAuthenticated()")
  public List<GalleryItemDto> listGallery(
      @PathVariable UUID id,
      Authentication authentication) {
    UUID callerId = getUserId(authentication);
    Role callerRole = getUserRole(authentication);
    return galleryService.listGallery(id, callerId, callerRole);
  }

  @DeleteMapping("/api/gallery/{itemId}")
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteMedia(
      @PathVariable UUID itemId,
      Authentication authentication) {
    UUID callerId = getUserId(authentication);
    Role callerRole = getUserRole(authentication);
    galleryService.deleteItem(itemId, callerId, callerRole);
  }

  private UUID getUserId(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    return UUID.fromString(auth.getName());
  }

  private Role getUserRole(Authentication auth) {
    if (auth == null) return Role.READER;
    if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
      return Role.ADMIN;
    }
    if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZER"))) {
      return Role.ORGANIZER;
    }
    return Role.READER;
  }
}
