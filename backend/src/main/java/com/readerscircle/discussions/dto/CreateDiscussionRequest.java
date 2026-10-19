package com.readerscircle.discussions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create-discussion form fields (book panel + discussion card). */
public record CreateDiscussionRequest(
    @NotBlank @Size(max = 255) String bookTitle,
    @NotBlank @Size(max = 255) String author,
    @Size(max = 120) String genre,
    Integer pageCount,
    Integer publicationYear,
    @Size(max = 1000) String coverImageUrl,
    String keyQuote,
    @Size(max = 255) String quoteAuthor,
    @Size(max = 255) String discussionTitle,
    String discussionExcerpt,
    @Size(max = 120) String tag,
    java.util.List<String> tags,
    String circleId) {

  public CreateDiscussionRequest(
      String bookTitle,
      String author,
      String genre,
      Integer pageCount,
      Integer publicationYear,
      String coverImageUrl,
      String keyQuote,
      String quoteAuthor,
      String discussionTitle,
      String discussionExcerpt,
      String tag,
      String circleId) {
    this(
        bookTitle,
        author,
        genre,
        pageCount,
        publicationYear,
        coverImageUrl,
        keyQuote,
        quoteAuthor,
        discussionTitle,
        discussionExcerpt,
        tag,
        null,
        circleId);
  }

  public CreateDiscussionRequest(
      String bookTitle,
      String author,
      String genre,
      Integer pageCount,
      Integer publicationYear,
      String coverImageUrl,
      String keyQuote,
      String quoteAuthor,
      String discussionTitle,
      String discussionExcerpt,
      String tag) {
    this(
        bookTitle,
        author,
        genre,
        pageCount,
        publicationYear,
        coverImageUrl,
        keyQuote,
        quoteAuthor,
        discussionTitle,
        discussionExcerpt,
        tag,
        null,
        null);
  }
}
