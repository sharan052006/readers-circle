package com.readerscircle.notifications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-user notification toggles backing Notification Settings
 * (Flyway V16 {@code notification_preferences}). One row per user.
 */
@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "join_request", nullable = false)
  private boolean joinRequest = true;

  @Column(name = "member_joined", nullable = false)
  private boolean memberJoined = true;

  @Column(name = "event_created", nullable = false)
  private boolean eventCreated = true;

  @Column(name = "event_registration", nullable = false)
  private boolean eventRegistration = true;

  @Column(name = "book_recommendation", nullable = false)
  private boolean bookRecommendation = true;

  @Column(nullable = false)
  private boolean discussion = true;

  @Column(nullable = false)
  private boolean report = true;

  @Column(name = "chat_message", nullable = false, columnDefinition = "boolean default true")
  private boolean chatMessage = true;

  @Column(name = "replies", nullable = false, columnDefinition = "boolean default true")
  private boolean replies = true;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected NotificationPreference() {}

  public NotificationPreference(UUID userId) {
    this.userId = userId;
  }

  public UUID getUserId() {
    return userId;
  }

  public boolean isJoinRequest() {
    return joinRequest;
  }

  public void setJoinRequest(boolean joinRequest) {
    this.joinRequest = joinRequest;
  }

  public boolean isMemberJoined() {
    return memberJoined;
  }

  public void setMemberJoined(boolean memberJoined) {
    this.memberJoined = memberJoined;
  }

  public boolean isEventCreated() {
    return eventCreated;
  }

  public void setEventCreated(boolean eventCreated) {
    this.eventCreated = eventCreated;
  }

  public boolean isEventRegistration() {
    return eventRegistration;
  }

  public void setEventRegistration(boolean eventRegistration) {
    this.eventRegistration = eventRegistration;
  }

  public boolean isBookRecommendation() {
    return bookRecommendation;
  }

  public void setBookRecommendation(boolean bookRecommendation) {
    this.bookRecommendation = bookRecommendation;
  }

  public boolean isDiscussion() {
    return discussion;
  }

  public void setDiscussion(boolean discussion) {
    this.discussion = discussion;
  }

  public boolean isReport() {
    return report;
  }

  public void setReport(boolean report) {
    this.report = report;
  }

  public boolean isChatMessage() {
    return chatMessage;
  }

  public void setChatMessage(boolean chatMessage) {
    this.chatMessage = chatMessage;
  }

  public boolean isReplies() {
    return replies;
  }

  public void setReplies(boolean replies) {
    this.replies = replies;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
