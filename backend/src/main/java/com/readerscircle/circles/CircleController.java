package com.readerscircle.circles;

import com.readerscircle.circles.dto.CircleDetailDto;
import com.readerscircle.circles.dto.CircleSummaryDto;
import com.readerscircle.circles.dto.CreateCircleRequest;
import com.readerscircle.circles.dto.UpdateCircleRequest;
import jakarta.validation.Valid;
import java.util.List;
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

@RestController
@RequestMapping("/api/circles")
public class CircleController {

  private final CircleService circleService;

  public CircleController(CircleService circleService) {
    this.circleService = circleService;
  }

  @GetMapping
  public List<CircleSummaryDto> listCircles(
      @RequestParam(name = "city", required = false) String city,
      Authentication authentication) {
    boolean isAdmin = checkIsAdmin(authentication);
    return circleService.listCircles(city, isAdmin);
  }

  @GetMapping("/{id}")
  public CircleDetailDto getCircle(
      @PathVariable UUID id,
      Authentication authentication) {
    boolean isAdmin = checkIsAdmin(authentication);
    return circleService.getCircle(id, isAdmin);
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public CircleDetailDto createCircle(@Valid @RequestBody CreateCircleRequest req) {
    return circleService.createCircle(req);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public CircleDetailDto updateCircle(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateCircleRequest req) {
    return circleService.updateCircle(id, req);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivateCircle(@PathVariable UUID id) {
    circleService.deactivateCircle(id);
  }

  private boolean checkIsAdmin(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) return false;
    return auth.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
  }
}
