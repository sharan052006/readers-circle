package com.readerscircle.circles.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.readerscircle.circles.CircleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateCircleRequest(
    @NotBlank(message = "Circle name is required") @Size(max = 50, message = "Circle name must be at most 50 characters") String name,
    @NotBlank(message = "City is required") String city,
    @NotBlank(message = "Description is required") @Size(max = 300, message = "Description must be at most 300 characters") String description,
    @NotNull(message = "Organizer ID is required") UUID organizerId,
    String coverImageUrl,
    @Size(max = 100, message = "Category must be at most 100 characters") String category,
    java.util.List<String> tags,
    CircleStatus status) {

  public CreateCircleRequest(String name, String city, String description, UUID organizerId) {
    this(name, city, description, organizerId, null, null, null, null);
  }

  public CreateCircleRequest(
      String name, String city, String description, UUID organizerId, String coverImageUrl) {
    this(name, city, description, organizerId, coverImageUrl, null, null, null);
  }

  @JsonCreator
  public CreateCircleRequest(
      @JsonProperty("name") String name,
      @JsonProperty("city") String city,
      @JsonProperty("description") String description,
      @JsonProperty("organizerId") UUID organizerId,
      @JsonProperty("organizerUserId") UUID organizerUserId,
      @JsonProperty("coverImageUrl") String coverImageUrl,
      @JsonProperty("category") String category,
      @JsonProperty("tags") java.util.List<String> tags,
      @JsonProperty("status") CircleStatus status,
      @JsonProperty("active") Boolean active) {
    this(
        name,
        city,
        description,
        organizerId != null ? organizerId : organizerUserId,
        coverImageUrl,
        category,
        tags,
        status != null ? status : (active != null ? (active ? CircleStatus.ACTIVE : CircleStatus.INACTIVE) : null));
  }
}
