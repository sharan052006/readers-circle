package com.readerscircle.gallery.dto;

import com.readerscircle.gallery.MediaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddGalleryMediaRequest(
    @NotNull MediaType mediaType,
    @NotBlank @Size(max = 1000) String mediaUrl,
    @Size(max = 255) String caption
) {}
