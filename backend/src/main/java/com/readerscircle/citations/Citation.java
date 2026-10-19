package com.readerscircle.citations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "citations")
public class Citation {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  @Column(name = "book_title", nullable = false)
  private String bookTitle;

  @Column(nullable = false)
  private String author;

  @Column(name = "quote_snippet", nullable = false, columnDefinition = "TEXT")
  private String quoteSnippet;

  @Column(name = "source_url", length = 500)
  private String sourceUrl;

  @Column(name = "circle_id")
  private UUID circleId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected Citation() {}

  public Citation(String bookTitle, String author, String quoteSnippet, String sourceUrl, UUID circleId) {
    this.bookTitle = bookTitle;
    this.author = author;
    this.quoteSnippet = quoteSnippet;
    this.sourceUrl = sourceUrl;
    this.circleId = circleId;
    this.createdAt = Instant.now();
  }

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

  public String getQuoteSnippet() {
    return quoteSnippet;
  }

  public void setQuoteSnippet(String quoteSnippet) {
    this.quoteSnippet = quoteSnippet;
  }

  public String getSourceUrl() {
    return sourceUrl;
  }

  public void setSourceUrl(String sourceUrl) {
    this.sourceUrl = sourceUrl;
  }

  public UUID getCircleId() {
    return circleId;
  }

  public void setCircleId(UUID circleId) {
    this.circleId = circleId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
