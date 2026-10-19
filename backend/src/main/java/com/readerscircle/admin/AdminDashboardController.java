package com.readerscircle.admin;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only aggregated endpoints for the Admin Dashboard screenshot.
 * All data is real (no mocks): top stat cards with 7-day trends, recent
 * circles, upcoming events across all circles, last-7-days overview and
 * cumulative growth series (30d / 3m / 1y).
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

  private final AdminDashboardService service;

  public AdminDashboardController(AdminDashboardService service) {
    this.service = service;
  }

  @GetMapping("/dashboard")
  public ResponseEntity<Map<String, Object>> dashboard(
      @RequestParam(required = false, defaultValue = "30d") String range) {
    return ResponseEntity.ok(service.dashboard(range));
  }

  @GetMapping("/analytics")
  public ResponseEntity<Map<String, Object>> analytics(
      @RequestParam(required = false, defaultValue = "30d") String range) {
    return ResponseEntity.ok(service.analytics(range));
  }
}
