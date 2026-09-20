package com.readerscircle.circles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateCircleRequest(
    @NotBlank(message = "Circle name is required") String name,
    @NotBlank(message = "City is required") String city,
    @NotBlank(message = "Description is required") String description,
    @NotNull(message = "Organizer ID is required") UUID organizerId) {}
