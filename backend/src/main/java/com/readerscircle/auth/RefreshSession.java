package com.readerscircle.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Server-side refresh-token record backing Active Sessions + Sign out of all
 * devices (Flyway V16 {@code refresh_sessions}). Only the SHA-256 hash is stored.
 */
@Entity
@Table(name = "refresh_sessions")
public class RefreshSession {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "device_info", length = 500)
  private String deviceInfo;

  @Column(name = "ip_address", length = 100)
  private String ipAddress;

  @Column(nullable = false)
  private boolean revoked = false;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "last_used_at", nullable = false)
  private Instant lastUsedAt;

  protected RefreshSession() {}

  public RefreshSession(UUID userId, String tokenHash, String deviceInfo, String ipAddress) {
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.deviceInfo = deviceInfo;
    this.ipAddress = ipAddress;
    this.lastUsedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public void setTokenHash(String tokenHash) {
    this.tokenHash = tokenHash;
  }

  public String getDeviceInfo() {
    return deviceInfo;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public boolean isRevoked() {
    return revoked;
  }

  public void setRevoked(boolean revoked) {
    this.revoked = revoked;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getLastUsedAt() {
    return lastUsedAt;
  }

  public void setLastUsedAt(Instant lastUsedAt) {
    this.lastUsedAt = lastUsedAt;
  }
}
