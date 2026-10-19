package com.readerscircle.testimonials;

import java.util.UUID;

public record TestimonialDto(
    UUID id,
    String memberName,
    String memberRole,
    String profilePhotoUrl,
    String circleName,
    String quote,
    int rating
) {}
