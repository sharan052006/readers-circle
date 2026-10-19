package com.readerscircle.recommendations;

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
 * Book recommendation card (Flyway V14 {@code recommendations} table).
 */
@Entity
@Table(name = "recommendations")
public class Recommendation {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "book_title", nullable = false, length = 255)
  private String bookTitle;

  @Column(nullable = false, length = 255)
  private String author;

  @Column(nullable = false, length = 120)
  private String genre = "General";

  @Column(nullable = false)
  private int rating = 5;

  @Column(name = "review_text", columnDefinition = "TEXT")
  private String reviewText;

  @Column(nullable = false)
  private boolean spoiler = false;

  @Column(length = 1000)
  private String tags;

  @Column(name = "cover_image_url", columnDefinition = "TEXT")
  private String coverImageUrl;

  @Column(name = "recommended_by", nullable = false)
  private UUID recommendedBy;

  @Column(name = "upvotes_count", nullable = false)
  private int upvotesCount = 0;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public Recommendation() {}

  public UUID getId() {
    return id;
  }

  public String getBookTitle() {
    return bookTitle;
  }

  public void setBookTitle(String bookTitle) {
    this.bookTitle = bookTitle;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  public String getGenre() {
    return genre;
  }

  public void setGenre(String genre) {
    this.genre = genre;
  }

  public int getRating() {
    return rating;
  }

  public void setRating(int rating) {
    this.rating = rating;
  }

  public String getReviewText() {
    return reviewText;
  }

  public void setReviewText(String reviewText) {
    this.reviewText = reviewText;
  }

  public boolean isSpoiler() {
    return spoiler;
  }

  public void setSpoiler(boolean spoiler) {
    this.spoiler = spoiler;
  }

  public String getTags() {
    return tags;
  }

  public void setTags(String tags) {
    this.tags = tags;
  }

  public String getCoverImageUrl() {
    return coverImageUrl;
  }

  public void setCoverImageUrl(String coverImageUrl) {
    this.coverImageUrl = coverImageUrl;
  }

  public UUID getRecommendedBy() {
    return recommendedBy;
  }

  public void setRecommendedBy(UUID recommendedBy) {
    this.recommendedBy = recommendedBy;
  }

  public int getUpvotesCount() {
    return upvotesCount;
  }

  public void setUpvotesCount(int upvotesCount) {
    this.upvotesCount = upvotesCount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
