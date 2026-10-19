package com.readerscircle.citations;

import java.util.UUID;

public record CitationDto(
    UUID id,
    String bookTitle,
    String author,
    String quoteSnippet,
    String sourceUrl
) {}
