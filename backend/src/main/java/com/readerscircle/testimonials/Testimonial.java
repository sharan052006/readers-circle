package com.readerscircle.testimonials;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "testimonials")
public class Testimonial {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  @Column(name = "member_name", nullable = false, length = 150)
  private String memberName;

  @Column(name = "member_role", length = 50)
  private String memberRole = "Reader";

  @Column(name = "profile_photo_url", columnDefinition = "TEXT")
  private String profilePhotoUrl;

  @Column(name = "circle_name", nullable = false, length = 200)
  private String circleName;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String quote;

  @Column(nullable = false)
  private int rating = 5;

  @Column(nullable = false)
  private boolean approved = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected Testimonial() {}

  public Testimonial(
      String memberName,
      String memberRole,
      String profilePhotoUrl,
      String circleName,
      String quote,
      int rating,
      boolean approved
  ) {
    this.memberName = memberName;
    this.memberRole = memberRole != null ? memberRole : "Reader";
    this.profilePhotoUrl = profilePhotoUrl;
    this.circleName = circleName;
    this.quote = quote;
    this.rating = rating;
    this.approved = approved;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getMemberName() {
    return memberName;
  }

  public void setMemberName(String memberName) {
    this.memberName = memberName;
  }

  public String getMemberRole() {
    return memberRole;
  }

  public void setMemberRole(String memberRole) {
    this.memberRole = memberRole;
  }

  public String getProfilePhotoUrl() {
    return profilePhotoUrl;
  }

  public void setProfilePhotoUrl(String profilePhotoUrl) {
    this.profilePhotoUrl = profilePhotoUrl;
  }

  public String getCircleName() {
    return circleName;
  }

  public void setCircleName(String circleName) {
    this.circleName = circleName;
  }

  public String getQuote() {
    return quote;
  }

  public void setQuote(String quote) {
    this.quote = quote;
  }

  public int getRating() {
    return rating;
  }

  public void setRating(int rating) {
    this.rating = rating;
  }

  public boolean isApproved() {
    return approved;
  }

  public void setApproved(boolean approved) {
    this.approved = approved;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
