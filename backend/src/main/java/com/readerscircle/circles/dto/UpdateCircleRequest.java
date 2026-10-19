package com.readerscircle.circles.dto;

import com.readerscircle.circles.CircleStatus;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateCircleRequest(
    @Size(max = 50, message = "Circle name must be at most 50 characters") String name,
    String city,
    @Size(max = 300, message = "Description must be at most 300 characters") String description,
    UUID organizerId,
    CircleStatus status,
    String coverImageUrl,
    @Size(max = 100, message = "Category must be at most 100 characters") String category,
    java.util.List<String> tags,
    Boolean active) {

  public UpdateCircleRequest(
      String name, String city, String description, UUID organizerId, CircleStatus status) {
    this(name, city, description, organizerId, status, null, null, null, null);
  }

  public UpdateCircleRequest(
      String name,
      String city,
      String description,
      UUID organizerId,
      CircleStatus status,
      String coverImageUrl) {
    this(name, city, description, organizerId, status, coverImageUrl, null, null, null);
  }
}
