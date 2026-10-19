package com.readerscircle.discussions;

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
 * Book discussion root (Flyway V13 {@code discussions} table).
 * One row per book card in the discussions list / view screens.
 */
@Entity
@Table(name = "discussions")
public class Discussion {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "book_title", nullable = false, length = 255)
  private String bookTitle;

  @Column(nullable = false, length = 255)
  private String author;

  @Column(nullable = false, length = 120)
  private String genre = "General";

  @Column(name = "page_count")
  private Integer pageCount;

  @Column(name = "publication_year")
  private Integer publicationYear;

  @Column(name = "cover_image_url", columnDefinition = "TEXT")
  private String coverImageUrl;

  @Column(name = "key_quote", columnDefinition = "TEXT")
  private String keyQuote;

  @Column(name = "quote_author", length = 255)
  private String quoteAuthor;

  @Column(name = "discussion_title", nullable = false, length = 255)
  private String discussionTitle;

  @Column(name = "discussion_excerpt", columnDefinition = "TEXT")
  private String discussionExcerpt;

  @Column(length = 120)
  private String tag;

  @Column(length = 1000)
  private String tags;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "circle_id")
  private UUID circleId;

  @Column(name = "likes_count", nullable = false)
  private int likesCount = 0;

  @Column(name = "views_count", nullable = false)
  private int viewsCount = 0;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public Discussion() {}

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
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

  public Integer getPageCount() {
    return pageCount;
  }

  public void setPageCount(Integer pageCount) {
    this.pageCount = pageCount;
  }

  public Integer getPublicationYear() {
    return publicationYear;
  }

  public void setPublicationYear(Integer publicationYear) {
    this.publicationYear = publicationYear;
  }

  public String getCoverImageUrl() {
    return coverImageUrl;
  }

  public void setCoverImageUrl(String coverImageUrl) {
    this.coverImageUrl = coverImageUrl;
  }

  public String getKeyQuote() {
    return keyQuote;
  }

  public void setKeyQuote(String keyQuote) {
    this.keyQuote = keyQuote;
  }

  public String getQuoteAuthor() {
    return quoteAuthor;
  }

  public void setQuoteAuthor(String quoteAuthor) {
    this.quoteAuthor = quoteAuthor;
  }

  public String getDiscussionTitle() {
    return discussionTitle;
  }

  public void setDiscussionTitle(String discussionTitle) {
    this.discussionTitle = discussionTitle;
  }

  public String getDiscussionExcerpt() {
    return discussionExcerpt;
  }

  public void setDiscussionExcerpt(String discussionExcerpt) {
    this.discussionExcerpt = discussionExcerpt;
  }

  public String getTag() {
    return tag;
  }

  public void setTag(String tag) {
    this.tag = tag;
  }

  public String getTags() {
    return tags;
  }

  public void setTags(String tags) {
    this.tags = tags;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(UUID createdBy) {
    this.createdBy = createdBy;
  }

  public int getViewsCount() {
    return viewsCount;
  }

  public void setViewsCount(int viewsCount) {
    this.viewsCount = viewsCount;
  }

  public UUID getCircleId() {
    return circleId;
  }

  public void setCircleId(UUID circleId) {
    this.circleId = circleId;
  }

  public int getLikesCount() {
    return likesCount;
  }

  public void setLikesCount(int likesCount) {
    this.likesCount = likesCount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
