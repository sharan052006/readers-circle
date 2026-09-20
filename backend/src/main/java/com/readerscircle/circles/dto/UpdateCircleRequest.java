package com.readerscircle.circles.dto;

import com.readerscircle.circles.CircleStatus;
import java.util.UUID;

public record UpdateCircleRequest(
    String name,
    String city,
    String description,
    UUID organizerId,
    CircleStatus status) {}
