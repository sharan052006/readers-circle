package com.readerscircle.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Platform settings for Reader's Circle (Flyway V29 admin_settings).
 * Stored persistently in PostgreSQL (or H2 in dev/test) instead of volatile memory.
 */
@Entity
@Table(name = "admin_settings")
public class AdminSettings {

  public static final String DEFAULT_ID = "default";
  public static final String DEFAULT_PLATFORM_NAME = "Reader's Circle";
  public static final String DEFAULT_TAGLINE = "Build a stronger reading community";
  public static final String DEFAULT_COMMUNITY_GUIDELINES =
      "Be respectful, cite sources, encourage diverse literary viewpoints, and avoid spoilers without tags.";
  public static final boolean DEFAULT_REGISTRATION_OPEN = true;
  public static final boolean DEFAULT_ALLOW_MEMBER_CIRCLE_CREATION = false;
  public static final int DEFAULT_MAX_CIRCLES_PER_MEMBER = 5;
  public static final boolean DEFAULT_EMAIL_NOTIFICATIONS_ENABLED = true;

  @Id
  @Column(length = 50)
  private String id = DEFAULT_ID;

  @Column(name = "platform_name", nullable = false, length = 255)
  private String platformName = DEFAULT_PLATFORM_NAME;

  @Column(name = "tagline", nullable = false, length = 500)
  private String tagline = DEFAULT_TAGLINE;

  @Column(name = "community_guidelines", nullable = false, columnDefinition = "TEXT")
  private String communityGuidelines = DEFAULT_COMMUNITY_GUIDELINES;

  @Column(name = "registration_open", nullable = false)
  private boolean registrationOpen = DEFAULT_REGISTRATION_OPEN;

  @Column(name = "allow_member_circle_creation", nullable = false)
  private boolean allowMemberCircleCreation = DEFAULT_ALLOW_MEMBER_CIRCLE_CREATION;

  @Column(name = "max_circles_per_member", nullable = false)
  private int maxCirclesPerMember = DEFAULT_MAX_CIRCLES_PER_MEMBER;

  @Column(name = "email_notifications_enabled", nullable = false)
  private boolean emailNotificationsEnabled = DEFAULT_EMAIL_NOTIFICATIONS_ENABLED;

  @Column(name = "custom_settings_json", columnDefinition = "TEXT")
  private String customSettingsJson;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public AdminSettings() {
    this.id = DEFAULT_ID;
  }

  public AdminSettings(String id) {
    this.id = id != null ? id : DEFAULT_ID;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getPlatformName() {
    return platformName;
  }

  public void setPlatformName(String platformName) {
    this.platformName = platformName;
  }

  public String getTagline() {
    return tagline;
  }

  public void setTagline(String tagline) {
    this.tagline = tagline;
  }

  public String getCommunityGuidelines() {
    return communityGuidelines;
  }

  public void setCommunityGuidelines(String communityGuidelines) {
    this.communityGuidelines = communityGuidelines;
  }

  public boolean isRegistrationOpen() {
    return registrationOpen;
  }

  public void setRegistrationOpen(boolean registrationOpen) {
    this.registrationOpen = registrationOpen;
  }

  public boolean isAllowMemberCircleCreation() {
    return allowMemberCircleCreation;
  }

  public void setAllowMemberCircleCreation(boolean allowMemberCircleCreation) {
    this.allowMemberCircleCreation = allowMemberCircleCreation;
  }

  public int getMaxCirclesPerMember() {
    return maxCirclesPerMember;
  }

  public void setMaxCirclesPerMember(int maxCirclesPerMember) {
    this.maxCirclesPerMember = maxCirclesPerMember;
  }

  public boolean isEmailNotificationsEnabled() {
    return emailNotificationsEnabled;
  }

  public void setEmailNotificationsEnabled(boolean emailNotificationsEnabled) {
    this.emailNotificationsEnabled = emailNotificationsEnabled;
  }

  public String getCustomSettingsJson() {
    return customSettingsJson;
  }

  public void setCustomSettingsJson(String customSettingsJson) {
    this.customSettingsJson = customSettingsJson;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
