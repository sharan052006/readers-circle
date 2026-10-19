package com.readerscircle.admin;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform settings for the Admin Settings sidebar page. Backed by persistent database storage. */
@RestController
@RequestMapping("/api/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSettingsController {

  private final AdminSettingsService adminSettingsService;

  public AdminSettingsController(AdminSettingsService adminSettingsService) {
    this.adminSettingsService = adminSettingsService;
  }

  @GetMapping
  public ResponseEntity<Map<String, Object>> get() {
    return ResponseEntity.ok(adminSettingsService.getSettings());
  }

  @PutMapping
  public ResponseEntity<Map<String, Object>> update(@RequestBody Map<String, Object> patch) {
    return ResponseEntity.ok(adminSettingsService.updateSettings(patch));
  }
}
