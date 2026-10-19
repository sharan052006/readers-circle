package com.readerscircle.home;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check endpoint for Render, Docker, and monitoring services.
 * Serves /healthz and /api/public/health with a simple 200 OK.
 */
@RestController
public class HealthController {

  @GetMapping({"/healthz", "/api/public/health"})
  public Map<String, Object> health() {
    return Map.of(
        "status", "UP",
        "service", "readers-circle-backend",
        "timestamp", Instant.now().toString());
  }
}
