package com.readerscircle.chat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

/**
 * Online dots for the General Chat leaders panel. In-memory heartbeats with a
 * 5-minute window — accurate on a single instance; treat as best-effort
 * presence if the backend ever scales horizontally.
 */
@Service
public class PresenceService {

  static final Duration ONLINE_WINDOW = Duration.ofMinutes(5);

  private final ConcurrentMap<UUID, Instant> heartbeats = new ConcurrentHashMap<>();

  public void heartbeat(UUID userId) {
    heartbeats.put(userId, Instant.now());
    prune();
  }

  public boolean isOnline(UUID userId) {
    Instant last = heartbeats.get(userId);
    return last != null && Duration.between(last, Instant.now()).compareTo(ONLINE_WINDOW) < 0;
  }

  public Instant lastSeen(UUID userId) {
    return heartbeats.get(userId);
  }

  private void prune() {
    Instant cutoff = Instant.now().minus(ONLINE_WINDOW).minus(Duration.ofMinutes(1));
    heartbeats.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
  }
}
