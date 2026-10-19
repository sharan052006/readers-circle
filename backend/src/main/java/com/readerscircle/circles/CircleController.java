package com.readerscircle.circles;

import com.readerscircle.circles.dto.CircleDetailDto;
import com.readerscircle.circles.dto.CircleSummaryDto;
import com.readerscircle.circles.dto.CreateCircleRequest;
import com.readerscircle.circles.dto.UpdateCircleRequest;
import com.readerscircle.gallery.FileStorageService;
import com.readerscircle.gallery.MediaType;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/circles")
public class CircleController {

  private final CircleService circleService;
  private final FileStorageService fileStorageService;

  public CircleController(CircleService circleService, FileStorageService fileStorageService) {
    this.circleService = circleService;
    this.fileStorageService = fileStorageService;
  }

  @GetMapping
  public List<CircleSummaryDto> listCircles(
      @RequestParam(name = "city", required = false) String city,
      @RequestParam(name = "search", required = false) String search,
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "sort", required = false) String sort,
      @RequestParam(name = "categories", required = false) String categories,
      @RequestParam(name = "minMembers", required = false) Long minMembers,
      @RequestParam(name = "maxMembers", required = false) Long maxMembers,
      Authentication authentication) {
    boolean isAdmin = checkIsAdmin(authentication);
    return circleService.listCircles(city, search, status, sort, categories, minMembers, maxMembers, isAdmin);
  }

  @GetMapping("/{id}")
  public CircleDetailDto getCircle(
      @PathVariable UUID id,
      Authentication authentication) {
    boolean isAdmin = checkIsAdmin(authentication);
    return circleService.getCircle(id, isAdmin);
  }

  /** Header counts + mini-cards for the circle detail Overview tab. */
  @GetMapping("/{id}/overview")
  public Map<String, Object> getCircleOverview(
      @PathVariable UUID id,
      Authentication authentication) {
    boolean isAdmin = checkIsAdmin(authentication);
    return circleService.getCircleOverview(id, isAdmin);
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public CircleDetailDto createCircle(@Valid @RequestBody CreateCircleRequest req) {
    return circleService.createCircle(req);
  }

  /**
   * Circle cover upload for the Create/Edit modal (JPG/PNG/WEBP via shared
   * gallery storage, served from /api/media). Admin or the circle's organizer.
   */
  @PostMapping(value = "/{id}/cover", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("isAuthenticated()")
  public CircleDetailDto uploadCover(
      @PathVariable UUID id,
      @RequestParam("file") MultipartFile file,
      Authentication authentication) {
    if (fileStorageService == null) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cover storage unavailable");
    }
    boolean isAdmin = checkIsAdmin(authentication);
    circleService.getCircle(id, isAdmin);
    UUID actorId = getUserId(authentication);
    if (!isAdmin && !circleService.isEffectiveOrganizer(id, actorId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the organizer or admin can change the cover");
    }
    String url = fileStorageService.storeFile(file, MediaType.PHOTO);
    return circleService.setCoverImageUrl(id, url);
  }

  /**
   * Edit circle (name/city/description/cover/category/tags). Admins may also
   * change organizer/status; the circle's organizer may not touch organizer,
   * status or active (privilege boundary) — those stay admin-only.
   */
  @PatchMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public CircleDetailDto updateCircle(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateCircleRequest req,
      Authentication authentication) {
    boolean isAdmin = checkIsAdmin(authentication);
    if (!isAdmin) {
      UUID actorId = getUserId(authentication);
      if (!circleService.isEffectiveOrganizer(id, actorId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the organizer or admin can edit this circle");
      }
      if (req.organizerId() != null || req.status() != null || req.active() != null) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, "Only an admin can change the organizer, status or active flag");
      }
    }
    return circleService.updateCircle(id, req);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivateCircle(@PathVariable UUID id) {
    circleService.deactivateCircle(id);
  }

  private UUID getUserId(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    return UUID.fromString(auth.getName());
  }

  private boolean checkIsAdmin(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) return false;
    return auth.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
  }
}
