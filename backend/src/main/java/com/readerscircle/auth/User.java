package com.readerscircle.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** Maps 1:1 to Flyway V1 {@code users} table. Role stored as VARCHAR (no PG enum type). */
@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, length = 255)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role = Role.READER;

  @Column(nullable = false)
  private boolean active = true;

  @Column(columnDefinition = "TEXT")
  private String bio;

  @Column(length = 120)
  private String city;

  @Column(length = 1000)
  private String interests;

  @Column(name = "profile_picture_url", length = 1000)
  private String profilePictureUrl;

  @Column(name = "blocked_reason", length = 500)
  private String blockedReason;

  @Column(name = "blocked_at")
  private OffsetDateTime blockedAt;

  @Column(name = "two_factor_enabled", nullable = false, columnDefinition = "boolean default false")
  private boolean twoFactorEnabled = false;

  @Column(name = "two_factor_secret", length = 100)
  private String twoFactorSecret;

  @Column(name = "token_version", nullable = false, columnDefinition = "integer default 0")
  private int tokenVersion = 0;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  protected User() {}

  public User(String name, String email, String passwordHash, Role role) {
    this(name, email, passwordHash, role, null, null);
  }

  public User(
      String name,
      String email,
      String passwordHash,
      Role role,
      String bio,
      String profilePictureUrl) {
    this.name = name;
    this.email = email;
    this.passwordHash = passwordHash;
    this.role = role;
    this.bio = bio;
    this.profilePictureUrl = profilePictureUrl;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public void setRole(Role role) {
    this.role = role;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public String getBio() {
    return bio;
  }

  public void setBio(String bio) {
    this.bio = bio;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getInterests() {
    return interests;
  }

  public void setInterests(String interests) {
    this.interests = interests;
  }

  public String getProfilePictureUrl() {
    return profilePictureUrl;
  }

  public void setProfilePictureUrl(String profilePictureUrl) {
    this.profilePictureUrl = profilePictureUrl;
  }

  public String getBlockedReason() {
    return blockedReason;
  }

  public void setBlockedReason(String blockedReason) {
    this.blockedReason = blockedReason;
  }

  public OffsetDateTime getBlockedAt() {
    return blockedAt;
  }

  public void setBlockedAt(OffsetDateTime blockedAt) {
    this.blockedAt = blockedAt;
  }

  public boolean isTwoFactorEnabled() {
    return twoFactorEnabled;
  }

  public void setTwoFactorEnabled(boolean twoFactorEnabled) {
    this.twoFactorEnabled = twoFactorEnabled;
  }

  public String getTwoFactorSecret() {
    return twoFactorSecret;
  }

  public void setTwoFactorSecret(String twoFactorSecret) {
    this.twoFactorSecret = twoFactorSecret;
  }

  public int getTokenVersion() {
    return tokenVersion;
  }

  public void setTokenVersion(int tokenVersion) {
    this.tokenVersion = tokenVersion;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
