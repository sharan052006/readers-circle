package com.readerscircle.circles;

import com.readerscircle.circles.dto.CircleSummaryDto;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** "Recommended for You" rail on the reader My Circles tab. */
@RestController
@PreAuthorize("isAuthenticated()")
public class RecommendedCirclesController {

  private final CircleService circleService;

  public RecommendedCirclesController(CircleService circleService) {
    this.circleService = circleService;
  }

  @GetMapping("/api/users/me/recommended-circles")
  public List<CircleSummaryDto> recommended(
      @RequestParam(required = false, defaultValue = "4") int limit,
      Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    return circleService.recommendedFor(UUID.fromString(authentication.getName()), limit);
  }
}
